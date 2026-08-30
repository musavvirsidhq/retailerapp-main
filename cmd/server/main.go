package main

import (
	"context"
	"log"
	"net/http"
	"os"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/gorilla/sessions"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
	"github.com/Sivanandha02/retailapp/internal/handlers"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

func main() {
	ctx := context.Background()

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://retailapp:retailapp_dev@localhost:5432/retailapp?sslmode=disable"
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
	factoryHandler := handlers.NewFactoryHandler(queries)
	shopHandler := handlers.NewShopHandler(queries)
	purchaseHandler := handlers.NewPurchaseHandler(queries, pool)
	saleHandler := handlers.NewSaleHandler(queries, pool)
	paymentHandler := handlers.NewPaymentHandler(queries)
	reportHandler := handlers.NewReportHandler(queries)
	authHandler := handlers.NewAuthHandler(queries, store)

	r := chi.NewRouter()
	r.Use(middleware.Logger)

	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Access-Control-Allow-Origin", "http://localhost:5173")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
			w.Header().Set("Access-Control-Allow-Credentials", "true")
			if r.Method == "OPTIONS" {
				w.WriteHeader(http.StatusOK)
				return
			}
			next.ServeHTTP(w, r)
		})
	})

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
			r.Post("/", purchaseHandler.Create)
			r.Get("/{id}/items", purchaseHandler.GetItems)
		})

		r.Route("/api/sales", func(r chi.Router) {
			r.Get("/", saleHandler.List)
			r.Post("/", saleHandler.Create)
			r.Get("/{id}/items", saleHandler.GetItems)
		})

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

		// Admin-only: delete operations
		r.Group(func(r chi.Router) {
			r.Use(appMiddleware.RequireRole("admin"))
			r.Delete("/api/products/{id}", productHandler.Delete)
			r.Delete("/api/factories/{id}", factoryHandler.Delete)
			r.Delete("/api/shops/{id}", shopHandler.Delete)
		})
	})

	log.Println("server starting on :8080")
	if err := http.ListenAndServe(":8080", r); err != nil {
		log.Fatal(err)
	}
}
