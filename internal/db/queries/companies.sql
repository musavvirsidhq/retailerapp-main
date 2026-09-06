-- name: CreateCompany :one
INSERT INTO companies (company_name, company_code, joining_date, created_by)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetCompanyByID :one
SELECT * FROM companies WHERE id = $1;

-- name: GetCompanyByCode :one
SELECT * FROM companies WHERE company_code = $1;

-- name: ListCompanies :many
SELECT * FROM companies ORDER BY joining_date DESC;

-- name: UpdateCompanyStatus :one
UPDATE companies SET status = $2, modified_on = now(), modified_by = $3 WHERE id = $1
RETURNING *;
