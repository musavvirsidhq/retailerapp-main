package subscription

import (
	"testing"
	"time"
)

func day(daysFromNow int, now time.Time) time.Time {
	return now.AddDate(0, 0, daysFromNow)
}

func TestComputeStatus(t *testing.T) {
	now := time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC)

	cases := []struct {
		name     string
		stored   string
		expiry   time.Time
		expected string
	}{
		{"active and not yet expired", "ACTIVE", day(10, now), "ACTIVE"},
		{"trial and not yet expired", "TRIAL", day(1, now), "TRIAL"},
		{"active but expiry has passed", "ACTIVE", day(-1, now), "EXPIRED"},
		{"trial but expiry has passed", "TRIAL", day(-30, now), "EXPIRED"},
		{"suspended overrides expiry", "SUSPENDED", day(30, now), "SUSPENDED"},
		{"expires exactly now counts as not yet expired", "ACTIVE", now, "ACTIVE"},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got := ComputeStatus(c.stored, c.expiry, now)
			if got != c.expected {
				t.Errorf("ComputeStatus(%q, %v, now) = %q, want %q", c.stored, c.expiry, got, c.expected)
			}
		})
	}
}

func TestExtendExpiry(t *testing.T) {
	now := time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC)

	t.Run("still active: extension adds on top of existing expiry, no time lost", func(t *testing.T) {
		currentExpiry := time.Date(2027, 8, 31, 0, 0, 0, 0, time.UTC) // matches the spec's worked example
		got := ExtendExpiry(currentExpiry, now, 3, 0)
		want := time.Date(2027, 11, 30, 0, 0, 0, 0, time.UTC)
		if !got.Equal(want) {
			t.Errorf("ExtendExpiry = %v, want %v", got, want)
		}
	})

	t.Run("already expired: extension starts fresh from now", func(t *testing.T) {
		currentExpiry := day(-10, now)
		got := ExtendExpiry(currentExpiry, now, 1, 0)
		want := now.AddDate(0, 1, 0)
		if !got.Equal(want) {
			t.Errorf("ExtendExpiry = %v, want %v", got, want)
		}
	})

	t.Run("expiry exactly now is treated as expired, extension starts fresh", func(t *testing.T) {
		got := ExtendExpiry(now, now, 1, 0)
		want := now.AddDate(0, 1, 0)
		if !got.Equal(want) {
			t.Errorf("ExtendExpiry = %v, want %v", got, want)
		}
	})
}

func TestWarningLevel(t *testing.T) {
	cases := []struct {
		status   string
		days     int
		expected string
	}{
		{"ACTIVE", 45, ""},
		{"ACTIVE", 30, "SOON"},
		{"ACTIVE", 8, "SOON"},
		{"ACTIVE", 7, "URGENT"},
		{"ACTIVE", 1, "URGENT"},
		{"EXPIRED", -5, "EXPIRED"},
	}
	for _, c := range cases {
		got := WarningLevel(c.status, c.days)
		if got != c.expected {
			t.Errorf("WarningLevel(%q, %d) = %q, want %q", c.status, c.days, got, c.expected)
		}
	}
}
