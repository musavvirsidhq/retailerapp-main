-- name: NextBillNumber :one
INSERT INTO bill_sequences (company_id, bill_type, next_number)
VALUES ($1, $2, 1)
ON CONFLICT (company_id, bill_type) DO UPDATE SET next_number = bill_sequences.next_number + 1
RETURNING next_number;
