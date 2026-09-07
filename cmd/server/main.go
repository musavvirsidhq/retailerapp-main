package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/gorilla/sessions"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
	"github.com/Sivanandha02/retailapp/internal/handlers"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
	"github.com/Sivanandha02/retailapp/internal/subscription"
)

func main() {
	ctx := context.Background()

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://retailapp:retailapp_dev@localhost:55432/retailapp?sslmode=disable"
	}

	sessionKey := os.Getenv("SESSION_KEY")
	if sessionKey == "" {
		sessionKey = "dev-only-insecure-key-change-in-production"
	}

	pool, err := pgxpool.New(ctx, dbURL)
	if err != nil {
		log.Fatalf("unable to connect to database: %v", err)
	}
	defer pool.Close()

	if err := pool.Ping(ctx); err != nil {
		log.Fatalf("unable to ping database: %v", err)
	}
	log.Println("connected to database successfully")

	store := sessions.NewCookieStore([]byte(sessionKey))
	store.Options = &sessions.Options{
		Path:     "/",
		MaxAge:   86400 * 7, // 7 days
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	}

	queries := db.New(pool)
	productHandler := handlers.NewProductHandler(queries)
	categoryHandler := handlers.NewCategoryHandler(queries)
	unitHandler := handlers.NewUnitHandler(queries)
	factoryHandler := handlers.NewFactoryHandler(queries)
	shopHandler := handlers.NewShopHandler(queries)
	purchaseHandler := handlers.NewPurchaseHandler(queries, pool)
	saleHandler := handlers.NewSaleHandler(queries, pool)
	paymentHandler := handlers.NewPaymentHandler(queries)
	reportHandler := handlers.NewReportHandler(queries)
	authHandler := handlers.NewAuthHandler(queries, store)
	superAdminHandler := handlers.NewSuperAdminHandler(queries, pool)
	companyHandler := handlers.NewCompanyHandler(queries)
	billHandler := handlers.NewBillHandler(queries)

	// Looks up a company's current subscription status for RequireActiveSubscription without
	// the middleware package depending on db directly.
	subscriptionLookup := func(ctx context.Context, companyID int32) (string, bool) {
		sub, err := queries.GetLatestSubscriptionByCompany(ctx, companyID)
		if err != nil {
			return "", false
		}
		return subscription.ComputeStatus(sub.Status, sub.ExpiryDate.Time, time.Now()), true
	}

	r := chi.NewRouter()
	r.Use(middleware.Logger)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("ok"))
	})

	// Public auth routes
	r.Post("/api/auth/login", authHandler.Login)
	r.Post("/api/auth/logout", authHandler.Logout)
	r.Get("/api/auth/me", authHandler.Me)

	// Protected routes - require login
	r.Group(func(r chi.Router) {
		r.Use(appMiddleware.RequireAuth(store))

		r.Get("/api/units", unitHandler.List)
		r.Get("/api/categories", categoryHandler.List)
		r.Get("/api/categories/{id}/subcategories", categoryHandler.ListSubcategories)
		r.Get("/api/products/{id}/cost", productHandler.CurrentCost)
		r.Get("/api/company/subscription-status", companyHandler.SubscriptionStatus)

		// Super Admin: platform-level company/subscription management
		r.Group(func(r chi.Router) {
			r.Use(appMiddleware.RequireSuperAdmin())
			r.Post("/api/super-admin/companies", superAdminHandler.CreateCompany)
			r.Get("/api/super-admin/companies", superAdminHandler.ListCompanies)
			r.Get("/api/super-admin/companies/{id}", superAdminHandler.GetCompany)
			r.Post("/api/super-admin/companies/{id}/trial", superAdminHandler.GrantTrial)
			r.Post("/api/super-admin/companies/{id}/subscription", superAdminHandler.GrantSubscription)
			r.Post("/api/super-admin/companies/{id}/extend", superAdminHandler.ExtendSubscription)
			r.Get("/api/super-admin/companies/{id}/subscription", superAdminHandler.GetSubscription)
		})

		// Company-scoped routes: require an active (non-expired) subscription for writes.
		r.Group(func(r chi.Router) {
			r.Use(appMiddleware.RequireActiveSubscription(subscriptionLookup))

			// Registered as plain Posts (not r.Route, which would mount a sub-router at
			// "/api/categories" and clobber the plain GET already registered above for the
			// same exact path, turning it into a 405).
			r.Post("/api/categories", categoryHandler.Create)
			r.Post("/api/categories/{id}/subcategories", categoryHandler.CreateSubcategory)

			r.Route("/api/products", func(r chi.Router) {
				r.Get("/", productHandler.List)
				r.Post("/", productHandler.Create)
				r.Get("/{id}", productHandler.Get)
				r.Put("/{id}", productHandler.Update)
				r.Delete("/{id}", productHandler.Delete)
			})

			r.Route("/api/factories", func(r chi.Router) {
				r.Get("/", factoryHandler.List)
				r.Post("/", factoryHandler.Create)
				r.Get("/{id}", factoryHandler.Get)
				r.Put("/{id}", factoryHandler.Update)
				r.Delete("/{id}", factoryHandler.Delete)
			})

			r.Route("/api/shops", func(r chi.Router) {
				r.Get("/", shopHandler.List)
				r.Post("/", shopHandler.Create)
				r.Get("/{id}", shopHandler.Get)
				r.Put("/{id}", shopHandler.Update)
				r.Delete("/{id}", shopHandler.Delete)
			})

			r.Route("/api/purchases", func(r chi.Router) {
				r.Get("/", purchaseHandler.List)
				r.With(appMiddleware.RequirePurchaseAccess()).Post("/", purchaseHandler.Create)
				r.Get("/{id}/items", purchaseHandler.GetItems)
				r.With(appMiddleware.RequirePurchaseAccess()).Post("/bills/{id}/cancel", purchaseHandler.Cancel)
			})
			r.Get("/api/purchases/bills/{id}", billHandler.PurchaseDetail)
			r.Get("/api/purchases/bills/{id}/pdf", billHandler.PurchasePDF)

			r.Route("/api/sales", func(r chi.Router) {
				r.Get("/", saleHandler.List)
				r.With(appMiddleware.RequireSalesAccess()).Post("/", saleHandler.Create)
				r.Get("/{id}/items", saleHandler.GetItems)
				r.With(appMiddleware.RequireSalesAccess()).Post("/bills/{id}/cancel", saleHandler.Cancel)
			})
			r.Get("/api/sales/bills/{id}", billHandler.SaleDetail)
			r.Get("/api/sales/bills/{id}/pdf", billHandler.SalePDF)

			r.Route("/api/payments", func(r chi.Router) {
				r.Get("/", paymentHandler.List)
				r.Post("/", paymentHandler.Create)
			})

			r.Get("/api/shops/{id}/balance", paymentHandler.ShopBalance)
			r.Get("/api/factories/{id}/balance", paymentHandler.FactoryBalance)

			r.Get("/api/reports/dashboard", reportHandler.Dashboard)
			r.Get("/api/reports/shop-dues", reportHandler.ShopDues)
			r.Get("/api/reports/factory-payables", reportHandler.FactoryPayables)
			r.Get("/api/reports/low-stock", reportHandler.LowStock)

			// Company Admin: staff management
			r.Group(func(r chi.Router) {
				r.Use(appMiddleware.RequireCompanyAdmin())
				r.Route("/api/company/users", func(r chi.Router) {
					r.Get("/", companyHandler.ListUsers)
					r.Post("/", companyHandler.CreateUser)
					r.Put("/{id}/permissions", companyHandler.UpdatePermissions)
					r.Put("/{id}/disable", companyHandler.DisableUser)
				})
			})
		})
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Println("server starting on :" + port)
	if err := http.ListenAndServe(":"+port, r); err != nil {
		log.Fatal(err)
	}
}
