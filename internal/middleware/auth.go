package middleware

import (
	"context"
	"net/http"

	"github.com/gorilla/sessions"
)

type contextKey string

const (
	UserIDKey                contextKey = "user_id"
	CompanyIDKey             contextKey = "company_id"
	UserTypeKey              contextKey = "user_type"
	PurchaseAccessKey        contextKey = "purchase_access"
	SalesAccessKey           contextKey = "sales_access"
	SalesBelowCostApproveKey contextKey = "sales_below_cost_approve"
	SubscriptionStatusKey    contextKey = "subscription_status"
)

const (
	UserTypeSuperAdmin   = "SUPER_ADMIN"
	UserTypeCompanyAdmin = "COMPANY_ADMIN"
	UserTypeStaff        = "STAFF"
)

// SubscriptionStatusLookup is set by cmd/server/main.go to a function that returns the
// company's current subscription status (ACTIVE/TRIAL/EXPIRED/SUSPENDED) so RequireActiveSubscription
// can gate write requests without middleware depending directly on the db package.
type SubscriptionStatusLookup func(ctx context.Context, companyID int32) (string, bool)

func RequireAuth(store *sessions.CookieStore) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			session, _ := store.Get(r, "retailapp_session")
			userID, ok := session.Values["user_id"]
			if !ok {
				http.Error(w, "unauthorized", http.StatusUnauthorized)
				return
			}

			userType, _ := session.Values["user_type"].(string)
			purchaseAccess, _ := session.Values["purchase_access"].(bool)
			salesAccess, _ := session.Values["sales_access"].(bool)
			salesBelowCostApprove, _ := session.Values["sales_below_cost_approve"].(bool)

			ctx := context.WithValue(r.Context(), UserIDKey, userID)
			ctx = context.WithValue(ctx, UserTypeKey, userType)
			ctx = context.WithValue(ctx, PurchaseAccessKey, purchaseAccess)
			ctx = context.WithValue(ctx, SalesAccessKey, salesAccess)
			ctx = context.WithValue(ctx, SalesBelowCostApproveKey, salesBelowCostApprove)

			if companyID, ok := session.Values["company_id"]; ok && companyID != nil {
				ctx = context.WithValue(ctx, CompanyIDKey, companyID)
			}

			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// UserIDFromContext returns the authenticated user's id.
func UserIDFromContext(ctx context.Context) (int32, bool) {
	v, ok := ctx.Value(UserIDKey).(int32)
	return v, ok
}

// CompanyIDFromContext returns the authenticated user's company id. Every company-scoped
// handler must use this instead of trusting a client-supplied company id, so that changing
// an id in a request can never cross into another company's data.
func CompanyIDFromContext(ctx context.Context) (int32, bool) {
	v, ok := ctx.Value(CompanyIDKey).(int32)
	return v, ok
}

func UserTypeFromContext(ctx context.Context) string {
	v, _ := ctx.Value(UserTypeKey).(string)
	return v
}

func PurchaseAccessFromContext(ctx context.Context) bool {
	v, _ := ctx.Value(PurchaseAccessKey).(bool)
	return v
}

func SalesAccessFromContext(ctx context.Context) bool {
	v, _ := ctx.Value(SalesAccessKey).(bool)
	return v
}

func SalesBelowCostApproveFromContext(ctx context.Context) bool {
	v, _ := ctx.Value(SalesBelowCostApproveKey).(bool)
	return v
}

// RequireSuperAdmin restricts access to the platform-level SUPER_ADMIN.
func RequireSuperAdmin() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if UserTypeFromContext(r.Context()) != UserTypeSuperAdmin {
				http.Error(w, "forbidden: super admin only", http.StatusForbidden)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// RequireCompanyAdmin restricts access to a company's own COMPANY_ADMIN (SUPER_ADMIN is exempt
// from company-scoped checks entirely but is not expected to hit these routes).
func RequireCompanyAdmin() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ut := UserTypeFromContext(r.Context())
			if ut != UserTypeCompanyAdmin && ut != UserTypeSuperAdmin {
				http.Error(w, "forbidden: company admin only", http.StatusForbidden)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// RequirePurchaseAccess allows COMPANY_ADMIN always, and STAFF only when granted purchase_access.
func RequirePurchaseAccess() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ut := UserTypeFromContext(r.Context())
			if ut == UserTypeCompanyAdmin || ut == UserTypeSuperAdmin {
				next.ServeHTTP(w, r)
				return
			}
			if ut == UserTypeStaff && PurchaseAccessFromContext(r.Context()) {
				next.ServeHTTP(w, r)
				return
			}
			http.Error(w, "forbidden: purchase access required", http.StatusForbidden)
		})
	}
}

// RequireSalesAccess allows COMPANY_ADMIN always, and STAFF only when granted sales_access.
func RequireSalesAccess() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ut := UserTypeFromContext(r.Context())
			if ut == UserTypeCompanyAdmin || ut == UserTypeSuperAdmin {
				next.ServeHTTP(w, r)
				return
			}
			if ut == UserTypeStaff && SalesAccessFromContext(r.Context()) {
				next.ServeHTTP(w, r)
				return
			}
			http.Error(w, "forbidden: sales access required", http.StatusForbidden)
		})
	}
}

// RequireActiveSubscription blocks non-GET requests for COMPANY_ADMIN/STAFF users whose
// company subscription has expired. SUPER_ADMIN is always exempt. GET requests are always
// allowed so an expired company can still view its own data.
func RequireActiveSubscription(lookup SubscriptionStatusLookup) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if UserTypeFromContext(r.Context()) == UserTypeSuperAdmin {
				next.ServeHTTP(w, r)
				return
			}
			if r.Method == http.MethodGet {
				next.ServeHTTP(w, r)
				return
			}
			companyID, ok := CompanyIDFromContext(r.Context())
			if !ok {
				http.Error(w, "no company associated with this user", http.StatusForbidden)
				return
			}
			status, ok := lookup(r.Context(), companyID)
			if !ok {
				http.Error(w, "This company has no active subscription yet. Please contact the administrator to activate a trial or subscription.", http.StatusPaymentRequired)
				return
			}
			if status == "EXPIRED" || status == "SUSPENDED" {
				http.Error(w, "Your subscription has expired. Please contact the administrator or renew your annual subscription to continue using the service.", http.StatusPaymentRequired)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
