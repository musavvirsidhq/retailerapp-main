package handlers

import (
	"context"
	"errors"
	"fmt"

	"github.com/Sivanandha02/retailapp/internal/db"
)

var errInsufficientStock = errors.New("insufficient stock for one or more products")

type costConsumption struct {
	LayerID  int32
	Quantity float64
	UnitCost float64
}

// consumeFIFO draws qty units of product companyID/productID from its active cost layers,
// oldest first, decrementing quantity_remaining as it goes. It returns the total cost of the
// quantity drawn plus the exact per-layer breakdown, which the caller must persist as
// sale_item_cost_consumptions so a later cancellation can restore precisely rather than
// re-deriving FIFO after further purchases have happened.
func consumeFIFO(ctx context.Context, qtx *db.Queries, companyID, productID int32, qty float64) (float64, []costConsumption, error) {
	layers, err := qtx.ListActiveCostLayersForUpdate(ctx, db.ListActiveCostLayersForUpdateParams{
		CompanyID: companyID,
		ProductID: productID,
	})
	if err != nil {
		return 0, nil, err
	}

	remaining := qty
	var totalCost float64
	var consumptions []costConsumption

	for _, layer := range layers {
		if remaining <= 0 {
			break
		}
		available := numericToFloat(layer.QuantityRemaining)
		if available <= 0 {
			continue
		}
		take := remaining
		if take > available {
			take = available
		}
		unitCost := numericToFloat(layer.BuyingPrice)
		totalCost += take * unitCost
		consumptions = append(consumptions, costConsumption{LayerID: layer.ID, Quantity: take, UnitCost: unitCost})

		if err := qtx.ConsumeCostLayer(ctx, db.ConsumeCostLayerParams{ID: layer.ID, QuantityRemaining: numericFromFloat(take)}); err != nil {
			return 0, nil, err
		}
		remaining -= take
	}

	if remaining > 1e-9 {
		return 0, nil, fmt.Errorf("%w: no cost layers available to cover the remaining %.3f units", errInsufficientStock, remaining)
	}

	return totalCost, consumptions, nil
}
