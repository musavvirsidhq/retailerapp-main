package handlers

import (
	"net/http/httptest"
	"testing"
	"time"
)

func day(s string) time.Time {
	t, _ := time.Parse(dateLayout, s)
	return t
}

// The worked example from the Cycle 4 spec, section 4.6: opening 0, sale 5,000 unpaid, sale
// 6,000 with 1,000 paid, then a 2,000 payment leaves 8,000 pending. A cancelled sale in the
// middle must show up in the list but never move the balance.
func ledgerFixture() []ledgerEntry {
	return []ledgerEntry{
		{Type: "payment", ID: 91, Date: "2026-09-28", Paid: strPtr("2000.00"), PhotoCount: 1,
			date: day("2026-09-28"), delta: -200000, collected: 200000},
		{Type: "sale", ID: 142, Date: "2026-09-20", isBill: true, PhotoCount: 1,
			date: day("2026-09-20"), delta: 500000, billed: 600000, collected: 100000},
		{Type: "sale", ID: 130, Date: "2026-09-10", isBill: true, Cancelled: true, date: day("2026-09-10")},
		{Type: "sale", ID: 120, Date: "2026-09-05", isBill: true,
			date: day("2026-09-05"), delta: 500000, billed: 500000},
		{Type: "opening", ID: 1, Date: "2026-09-01", date: day("2026-09-01")},
	}
}

func TestBuildLedgerRunningBalance(t *testing.T) {
	resp, err := buildLedger(httptest.NewRequest("GET", "/", nil), ledgerParty{ID: 1}, ledgerFixture())
	if err != nil {
		t.Fatal(err)
	}
	if resp.Summary.Pending != "8000.00" || resp.Summary.TotalBilled != "11000.00" || resp.Summary.TotalCollected != "3000.00" {
		t.Fatalf("unexpected summary: %+v", resp.Summary)
	}
	if resp.Summary.LastPaymentAt == nil || *resp.Summary.LastPaymentAt != "2026-09-28" {
		t.Fatalf("expected last payment on 2026-09-28, got %v", resp.Summary.LastPaymentAt)
	}

	// Newest first, each row carrying the balance after it.
	want := []struct {
		id      int32
		balance string
	}{{91, "8000.00"}, {142, "10000.00"}, {130, "5000.00"}, {120, "5000.00"}, {1, "0.00"}}
	if len(resp.Entries) != len(want) {
		t.Fatalf("expected %d entries, got %d", len(want), len(resp.Entries))
	}
	for i, w := range want {
		if resp.Entries[i].ID != w.id || resp.Entries[i].BalanceAfter != w.balance {
			t.Fatalf("entry %d: want id %d balance %s, got id %d balance %s",
				i, w.id, w.balance, resp.Entries[i].ID, resp.Entries[i].BalanceAfter)
		}
	}
}

func TestBuildLedgerFilters(t *testing.T) {
	resp, err := buildLedger(httptest.NewRequest("GET", "/?with_photos=true", nil), ledgerParty{}, ledgerFixture())
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.Entries) != 2 {
		t.Fatalf("with_photos: expected 2 entries, got %d", len(resp.Entries))
	}
	// Filters never change the all-time header.
	if resp.Summary.Pending != "8000.00" {
		t.Fatalf("filters must not change the pending total, got %s", resp.Summary.Pending)
	}

	resp, err = buildLedger(httptest.NewRequest("GET", "/?type=payment", nil), ledgerParty{}, ledgerFixture())
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.Entries) != 1 || resp.Entries[0].Type != "payment" {
		t.Fatalf("type=payment: got %+v", resp.Entries)
	}

	resp, err = buildLedger(httptest.NewRequest("GET", "/?from=2026-09-15&to=2026-09-30", nil), ledgerParty{}, ledgerFixture())
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.Entries) != 2 || resp.RangeSummary.Billed != "6000.00" || resp.RangeSummary.Collected != "3000.00" {
		t.Fatalf("date range: got %d entries, range %+v", len(resp.Entries), resp.RangeSummary)
	}

	if _, err := buildLedger(httptest.NewRequest("GET", "/?from=28-09-2026", nil), ledgerParty{}, ledgerFixture()); err == nil {
		t.Fatal("expected an error for a malformed from date")
	}
}

func TestSortAndFilterDues(t *testing.T) {
	t1, t2, t3 := day("2026-09-01"), day("2026-09-10"), day("2026-09-20")
	rows := []duesRow{
		{ID: 1, Name: "Alpha", Phone: "900", balance: 50000, LastActivityAt: &t1, LastPaymentAt: &t1},
		{ID: 2, Name: "Beta", Phone: "911", balance: 150000, LastActivityAt: &t3, LastPaymentAt: &t2},
		{ID: 3, Name: "Gamma", Phone: "922", balance: 0, LastActivityAt: &t2},
		{ID: 4, Name: "Delta", Phone: "933", balance: 10000},
	}

	resp := sortAndFilterDues(httptest.NewRequest("GET", "/", nil), rows)
	if resp.TotalPending != "2100.00" || resp.PendingCount != 3 {
		t.Fatalf("unexpected totals: %s / %d", resp.TotalPending, resp.PendingCount)
	}
	// Default "recent": zero balance hidden, never-active party last.
	if got := ids(resp.Rows); got != "2,1,4" {
		t.Fatalf("recent sort: got %s", got)
	}
	if got := ids(sortAndFilterDues(httptest.NewRequest("GET", "/?sort=amount", nil), rows).Rows); got != "2,1,4" {
		t.Fatalf("amount sort: got %s", got)
	}
	if got := ids(sortAndFilterDues(httptest.NewRequest("GET", "/?sort=oldest", nil), rows).Rows); got != "4,1,2" {
		t.Fatalf("oldest sort: got %s", got)
	}
	if got := ids(sortAndFilterDues(httptest.NewRequest("GET", "/?include_zero=true&sort=name", nil), rows).Rows); got != "1,2,4,3" {
		t.Fatalf("name sort with zero: got %s", got)
	}
	if got := ids(sortAndFilterDues(httptest.NewRequest("GET", "/?q=91", nil), rows).Rows); got != "2" {
		t.Fatalf("phone search: got %s", got)
	}
}

func ids(rows []duesRow) string {
	out := ""
	for i, r := range rows {
		if i > 0 {
			out += ","
		}
		out += string(rune('0' + r.ID))
	}
	return out
}

func TestFormatPaise(t *testing.T) {
	for in, want := range map[int64]string{0: "0.00", 5: "0.05", 123456: "1234.56", -250: "-2.50"} {
		if got := formatPaise(in); got != want {
			t.Fatalf("formatPaise(%d) = %s, want %s", in, got, want)
		}
	}
}
