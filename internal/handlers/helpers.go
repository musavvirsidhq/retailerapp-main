package handlers

import (
	"errors"
	"regexp"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgtype"
)

var phoneRegex = regexp.MustCompile(`^[0-9+][0-9 \-]{6,19}$`)

var (
	errInvalidPhone = errors.New("phone number format is invalid")
	errSamePhone    = errors.New("secondary contact number must be different from the primary contact number")
)

func pgTextOrNil(s string) pgtype.Text {
	if s == "" {
		return pgtype.Text{Valid: false}
	}
	return pgtype.Text{String: s, Valid: true}
}

var (
	errInvalidUnit        = errors.New("unit must be one of the predefined units")
	errInvalidCategory    = errors.New("category not found")
	errInvalidSubcategory = errors.New("subcategory not found or does not belong to the selected category")
)

// isUniqueViolation reports whether err is a Postgres unique-constraint violation (SQLSTATE 23505).
func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		return pgErr.Code == "23505"
	}
	return false
}

// isCheckViolation reports whether err is a Postgres check-constraint violation (SQLSTATE 23514).
func isCheckViolation(err error) bool {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		return pgErr.Code == "23514"
	}
	return false
}

func numericToFloat(n pgtype.Numeric) float64 {
	f, _ := n.Float64Value()
	return f.Float64
}

func pgInt4Valid(v int32) pgtype.Int4 {
	return pgtype.Int4{Int32: v, Valid: true}
}
