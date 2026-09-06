// Package subscription holds pure, DB-free business logic for subscription status and
// extension so the rules can be unit tested without a database.
package subscription

import "time"

// ComputeStatus derives the effective status of a subscription from its stored status and
// expiry date. A subscription that is ACTIVE or TRIAL but whose expiry_date has passed is
// reported as EXPIRED even though the stored row may still say otherwise (there is no
// background sweep job in this system - status is always computed on read).
func ComputeStatus(storedStatus string, expiryDate time.Time, now time.Time) string {
	if storedStatus == "SUSPENDED" {
		return "SUSPENDED"
	}
	if now.After(expiryDate) {
		return "EXPIRED"
	}
	return storedStatus
}

// ExtendExpiry implements the spec's extension rule: if the current subscription is still
// active (expiry is in the future), the additional period is added on top of the existing
// expiry date so no remaining time is lost. If it has already expired, the new period starts
// fresh from now.
func ExtendExpiry(currentExpiry time.Time, now time.Time, addMonths int, addDays int) time.Time {
	base := now
	if currentExpiry.After(now) {
		base = currentExpiry
	}
	return addMonthsClamped(base, addMonths).AddDate(0, 0, addDays)
}

// addMonthsClamped adds months the way a calendar would: 31-Aug + 3 months = 30-Nov, not
// 1-Dec. time.Time.AddDate rolls the day field into the following month whenever the target
// month is shorter than the start month, which quietly drifts subscription expiry dates
// forward - so the day is clamped to the last day of the target month instead.
func addMonthsClamped(t time.Time, months int) time.Time {
	year, month, day := t.Date()
	targetMonthIndex := int(month) - 1 + months
	targetYear := year + targetMonthIndex/12
	targetMonth := time.Month(targetMonthIndex%12 + 1)
	if targetMonthIndex%12 < 0 {
		targetMonth += 12
		targetYear--
	}
	lastDay := time.Date(targetYear, targetMonth+1, 0, 0, 0, 0, 0, t.Location()).Day()
	if day > lastDay {
		day = lastDay
	}
	return time.Date(targetYear, targetMonth, day, t.Hour(), t.Minute(), t.Second(), t.Nanosecond(), t.Location())
}

// DaysRemaining returns the whole number of days between now and expiry (negative once expired).
func DaysRemaining(expiryDate time.Time, now time.Time) int {
	d := expiryDate.Sub(now)
	days := int(d.Hours() / 24)
	if d > 0 && d.Hours() < 24 {
		return 1
	}
	return days
}

// WarningLevel classifies how urgently an expiry warning should be shown to company users.
// Returns one of "", "SOON" (<=30 days), "URGENT" (<=7 days), "EXPIRED".
func WarningLevel(status string, daysRemaining int) string {
	if status == "EXPIRED" {
		return "EXPIRED"
	}
	if daysRemaining <= 7 {
		return "URGENT"
	}
	if daysRemaining <= 30 {
		return "SOON"
	}
	return ""
}
