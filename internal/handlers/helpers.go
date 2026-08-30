package handlers

import "github.com/jackc/pgx/v5/pgtype"

func pgTextOrNil(s string) pgtype.Text {
	if s == "" {
		return pgtype.Text{Valid: false}
	}
	return pgtype.Text{String: s, Valid: true}
}
