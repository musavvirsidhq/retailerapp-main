package main

import (
	"context"
	"fmt"
	"log"
	"os"

	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/bcrypt"

	"github.com/Sivanandha02/retailapp/internal/db"
)

func main() {
	ctx := context.Background()

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://retailapp:retailapp_dev@localhost:5432/retailapp?sslmode=disable"
	}

	pool, err := pgxpool.New(ctx, dbURL)
	if err != nil {
		log.Fatalf("unable to connect: %v", err)
	}
	defer pool.Close()

	queries := db.New(pool)

	fmt.Print("Enter admin name: ")
	var name string
	fmt.Scanln(&name)

	fmt.Print("Enter admin username: ")
	var username string
	fmt.Scanln(&username)

	fmt.Print("Enter admin password: ")
	var password string
	fmt.Scanln(&password)

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		log.Fatalf("failed to hash password: %v", err)
	}

	user, err := queries.CreateUser(ctx, db.CreateUserParams{
		Name:         name,
		Username:     username,
		PasswordHash: string(hash),
		Role:         "admin",
	})
	if err != nil {
		log.Fatalf("failed to create user: %v", err)
	}

	fmt.Printf("Admin user created: %s (id=%d)\n", user.Username, user.ID)
}
