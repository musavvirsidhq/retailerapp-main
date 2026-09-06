-- name: CreateCostLayer :one
INSERT INTO product_cost_layers (company_id, product_id, purchase_bill_item_id, buying_price, quantity_received, quantity_remaining, created_by)
VALUES ($1, $2, $3, $4, $5, $5, $6)
RETURNING *;

-- name: ListActiveCostLayersForUpdate :many
SELECT * FROM product_cost_layers
WHERE company_id = $1 AND product_id = $2 AND status = 'ACTIVE' AND quantity_remaining > 0
ORDER BY created_on ASC
FOR UPDATE;

-- name: ConsumeCostLayer :exec
UPDATE product_cost_layers
SET quantity_remaining = quantity_remaining - $2,
    status = CASE WHEN quantity_remaining - $2 <= 0 THEN 'DEPLETED' ELSE status END
WHERE id = $1;

-- name: RestoreCostLayer :exec
UPDATE product_cost_layers
SET quantity_remaining = quantity_remaining + $2,
    status = CASE WHEN status = 'DEPLETED' AND quantity_remaining + $2 > 0 THEN 'ACTIVE' ELSE status END
WHERE id = $1;

-- name: GetCostLayerByPurchaseItem :one
SELECT * FROM product_cost_layers WHERE purchase_bill_item_id = $1;

-- name: CancelCostLayer :exec
UPDATE product_cost_layers SET status = 'CANCELLED', quantity_remaining = 0 WHERE id = $1;

-- name: CreateSaleItemCostConsumption :one
INSERT INTO sale_item_cost_consumptions (sale_item_id, product_cost_layer_id, quantity, unit_cost)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: ListCostConsumptionsBySale :many
SELECT sicc.* FROM sale_item_cost_consumptions sicc
JOIN sale_items si ON si.id = sicc.sale_item_id
WHERE si.sale_id = $1;
