# CYCLE 2 — PRODUCT, PRICING, BILLING, COMPANY & SUBSCRIPTION REQUIREMENTS

## 1. Cycle Overview

Cycle 2 focuses on enhancing the retailer/wholesale management system with:

- Product unit management
- Category and subcategory management
- Unique product codes
- Multiple purchase prices
- Selling price management
- Below-cost sales warnings
- Supplier and retailer secondary contacts
- Sales and purchase bill generation
- Bill printing and sharing
- Transaction cancellation/reversal
- Responsive mobile support
- Super Admin management
- Company management
- Company subscription management
- Trial access
- Subscription extension
- Company Admin and Staff management
- Staff purchase/sales permissions
- Subscription expiry warnings
- Multi-company data isolation

The implementation must extend the existing architecture and must not unnecessarily redesign existing services.

---

# 2. Product Unit Management

## 2.1 Requirement

Every product must have a unit.

The unit must be selected from a predefined dropdown.

## 2.2 Initial Units

The system should initially support:

- PIECE
- BOX
- PACK
- KG
- GRAM
- LITRE
- ML
- METER
- SET
- DOZEN

The system should allow additional units to be added in the future.

## 2.3 Business Rules

1. Unit is mandatory when creating a product.
2. Unit must be selected from the available unit list.
3. User cannot enter arbitrary unit text.
4. Unit must be displayed during purchase and sales.
5. Quantity must be handled according to the selected unit.
6. Historical transactions must retain their original unit information.

## 2.4 Example

Product:

- Name: Nike Shoe
- Product Code: NIKE-001
- Unit: PIECE
- Selling Price: ₹1,500

---

# 3. Product Category and Subcategory

## 3.1 Requirement

Every product must belong to a category.

A subcategory is optional.

## 3.2 Rules

- Category = REQUIRED
- Subcategory = OPTIONAL
- A subcategory must belong to the selected category.
- The user must not be able to select a subcategory belonging to another category.

## 3.3 Example

Category:

Electronics

Subcategory:

Mobile Phones

Product:

Samsung Galaxy S25

Another example:

Category:

Footwear

Subcategory:

None

Product:

Running Shoe

## 3.4 UI

Category:

[ Electronics ▼ ]

Subcategory:

[ Mobile Phones ▼ ]

If the selected category has no subcategory, the subcategory can remain empty.

---

# 4. Unique Product Code

## 4.1 Requirement

Every product must have a unique product code/SKU.

There is no barcode requirement.

## 4.2 Example

Product Code:

NIKE-SHOE-001

The following must not be allowed:

NIKE-SHOE-001
NIKE-SHOE-001

## 4.3 Validation

The backend must enforce product-code uniqueness.

Frontend validation alone is not sufficient.

## 4.4 Database Rule

The product code/SKU must have a UNIQUE constraint.

---

# 5. Product Selling Price

## 5.1 Requirement

A selling price must be available when creating a product.

The current selling price can be edited later.

## 5.2 Example

Initial product:

Product: Nike Shoe

Buying Price: ₹1,000

Selling Price: ₹1,500

Later:

Selling Price: ₹1,600

The current product selling price becomes ₹1,600.

## 5.3 Historical Price Rule

Changing the current selling price must NOT change historical sales bills.

Example:

Old sale:

Nike Shoe
Quantity: 2
Selling Price: ₹1,500

Later product selling price:

₹1,600

The old bill must continue to show:

₹1,500

---

# 6. Multiple Buying Prices / Cost Layers

## 6.1 Requirement

The same product may be purchased at different buying prices over time.

The system must NOT overwrite an old buying price when a new purchase has a different price.

## 6.2 Example

Product:

Coca Cola 500ml

First purchase:

Buying Price: ₹20
Quantity: 100
Selling Price: ₹25

Later purchase:

Buying Price: ₹22
Quantity: 100
Selling Price: ₹28

The system must retain both purchase prices.

## 6.3 Cost Structure

Product:

Coca Cola 500ml

Cost Layer 1:

- Buying Price: ₹20
- Quantity: 100

Cost Layer 2:

- Buying Price: ₹22
- Quantity: 100

The new purchase creates a new cost layer/batch.

## 6.4 Important Rule

A new buying price must never overwrite historical purchase prices.

Historical purchase bills must retain the actual price at which the product was purchased.

## 6.5 Recommended Inventory Costing

FIFO is recommended for inventory costing.

Example:

Available stock:

100 units @ ₹20
100 units @ ₹22

Sale:

120 units

FIFO cost:

100 × ₹20 = ₹2,000

20 × ₹22 = ₹440

Total Cost = ₹2,440

If sold at ₹28:

120 × ₹28 = ₹3,360

Profit:

₹3,360 - ₹2,440 = ₹920

The inventory costing method should be explicitly configured before implementing profit calculations.

---

# 7. New Selling Price After New Purchase Price

## 7.1 Requirement

When a new purchase price is entered, the user should be able to specify a new selling price.

Example:

Existing:

Buying Price: ₹20
Selling Price: ₹25

New purchase:

Buying Price: ₹22
New Selling Price: ₹28

The system should store the new selling price as the current/default selling price.

## 7.2 Historical Rule

The new selling price must only affect future sales.

Previous bills must remain unchanged.

---

# 8. Supplier / Factory Secondary Contact

## 8.1 Requirement

Factory/supplier records must support:

- Primary contact number
- Secondary contact number

## 8.2 Example

Factory:

ABC Manufacturing

Primary Contact:

9876543210

Secondary Contact:

9123456780

## 8.3 Rules

1. Secondary contact is optional.
2. Primary and secondary numbers should not be identical.
3. Phone number format must be validated.
4. Secondary contact must not replace the primary contact.

---

# 9. Retailer / Shop Secondary Contact

## 9.1 Requirement

Retailer/shop records must support:

- Primary contact number
- Secondary contact number

## 9.2 Example

Shop:

XYZ Stores

Primary Contact:

9876543210

Secondary Contact:

9123456780

The same validation rules as supplier contacts apply.

---

# 10. Sales Below Buying Price Warning

## 10.1 Requirement

When creating a sale, the system must compare the selling price with the applicable buying/cost price.

If:

Selling Price < Buying Price

the system must display a warning.

## 10.2 Example

Buying Price:

₹1,000

Selling Price:

₹900

Display:

WARNING: Selling price is below the buying price. This sale may result in a loss.

## 10.3 UI Behavior

The selling price field can be shown in a warning/red state.

Example:

Selling Price:

[ ₹900 ]

WARNING: Selling price is below buying price ₹1,000.

## 10.4 Permission

Initially, this should be a warning rather than an automatic block.

However, the system should support permission-based approval.

Possible permission:

SALES_BELOW_COST_APPROVE

A user without this permission cannot complete a below-cost sale if approval is required.

## 10.5 Backend Validation

The comparison must also be performed by the backend.

It must not depend only on frontend validation.

---

# 11. Sales Bill

## 11.1 Requirement

The system must generate a professional sales bill.

The bill must be:

- Printable
- Mobile-friendly
- PDF compatible
- Easy to share
- Suitable for WhatsApp sharing

## 11.2 Bill Information

Sales bill should contain:

- Company name
- Company contact information
- Bill number
- Bill date
- Customer/retailer information
- Product name
- Product code
- Unit
- Quantity
- Selling price
- Discount
- Tax
- Total
- Payment status

## 11.3 Bill Actions

The bill screen should provide:

[View Bill]

[Print]

[Download PDF]

[Share]

---

# 12. Purchase Bill

## 12.1 Requirement

The system must generate a professional purchase bill/document.

It should contain:

- Company information
- Supplier/factory information
- Bill number
- Bill date
- Product
- Product code
- Unit
- Quantity
- Purchase price
- Discount
- Tax
- Total
- Payment status

## 12.2 Actions

[View Bill]

[Print]

[Download PDF]

[Share]

---

# 13. WhatsApp Bill Sharing

## 13.1 Requirement

Bills should be easy to send through WhatsApp.

The system should support sharing the generated bill/PDF through the device's sharing mechanism where available.

On mobile devices, the user should be able to select WhatsApp from the available share options.

The system should not require a separate mobile application for this functionality.

---

# 14. Transaction Correction / Mistaken History

## 14.1 Requirement

If a transaction is accidentally created, the user must have a controlled method to correct it.

Possible transactions include:

- Sales bill
- Purchase bill
- Payment
- Stock adjustment
- Other financial transactions

## 14.2 Do Not Physically Delete Completed Financial History

Completed financial transactions must not simply be deleted from the database.

Instead, the system should use:

- CANCEL
- REVERSE

depending on the transaction type.

## 14.3 Example

Incorrect sale:

Sales Bill: SB-1005

Quantity:

100

Correct quantity:

10

The system should cancel/reverse SB-1005 and allow the correct transaction to be created.

## 14.4 Inventory

If the incorrect sale reduced stock:

Original:

-100

Cancellation/reversal:

+100

This maintains the correct stock movement history.

## 14.5 Cancellation Reason

The user must provide a reason.

Example:

Reason:

Wrong quantity entered.

## 14.6 Audit

The system must record:

- Who cancelled it
- Date/time
- Reason
- Original transaction
- Reversal/cancellation transaction
- Related inventory impact
- Related payment impact

---

# 15. Responsive Web Application

## 15.1 Requirement

The application must work properly on:

- Desktop
- Laptop
- Tablet
- Android mobile
- iPhone/mobile browser

The system should be a responsive web application.

A separate mobile application is not required for this cycle.

## 15.2 Mobile Requirements

On mobile:

- Navigation must be mobile-friendly.
- Forms must fit the screen.
- Buttons must be touch-friendly.
- Tables should become scrollable or card-based.
- Bill pages must be mobile-friendly.
- PDF generation must work.
- Bill sharing must work.
- Sales and purchase functionality must remain available.

## 15.3 Responsive Principle

The application must not be designed only for one fixed desktop resolution.

The UI must automatically adapt to different screen sizes.

---

# 16. Super Admin

## 16.1 Initial System Administrator

During initial system setup, the first administrator must be created as:

SUPER_ADMIN

## 16.2 Super Admin Responsibilities

The Super Admin can:

- Create companies
- View companies
- Manage company subscriptions
- Grant trial access
- Grant annual access
- Extend subscriptions
- View company joining dates
- View subscription expiry dates
- View subscription/payment information
- Manage platform-level configuration

---

# 17. Company Management

## 17.1 Company Creation

The Super Admin can create companies.

Example:

Company:

ABC Traders

Company Code:

ABC001

Joining Date:

01-Sep-2026

## 17.2 Company Hierarchy

SYSTEM

→ SUPER ADMIN

→ COMPANY

→ COMPANY ADMIN

→ STAFF USERS

Example:

SUPER ADMIN

    Company A
        Company Admin
            Staff
            Staff

    Company B
        Company Admin
            Staff
            Staff

---

# 18. Company Admin

## 18.1 Requirement

Each company must have a Company Admin.

The Company Admin is responsible for managing the company's users and business operations.

## 18.2 Company Admin Responsibilities

The Company Admin can:

- Manage company users
- Create staff
- Edit staff
- Assign staff permissions
- Manage products
- Manage purchases
- Manage sales
- Manage company business data
- View company information

The Company Admin must not be able to access another company's data.

---

# 19. Company Subscription

## 19.1 Requirement

Each company must have a subscription/access period.

The system must track:

- Joining date
- Subscription start date
- Subscription expiry date
- Subscription type
- Amount paid
- Purchase date
- Subscription status

## 19.2 Subscription Types

Initially:

- TRIAL
- ANNUAL
- EXTENDED

The design should allow additional plans later.

## 19.3 Company Status

Possible statuses:

- ACTIVE
- TRIAL
- EXPIRED
- SUSPENDED

---

# 20. Super Admin Company Dashboard

The Super Admin must be able to see all companies.

Example:

COMPANIES

Company | Joining Date | Expiry Date | Status

ABC Traders | 01-Jan-2026 | 01-Jan-2027 | ACTIVE

XYZ Wholesale | 15-Feb-2026 | 15-Feb-2027 | ACTIVE

Test Company | 20-Aug-2026 | 20-Sep-2026 | TRIAL

Old Company | 10-Jan-2025 | 10-Jan-2026 | EXPIRED

## 20.1 Super Admin Information

Super Admin should be able to view:

- Total companies
- Company name
- Company joining date
- Subscription start date
- Subscription expiry date
- Trial status
- Active/expired status
- Subscription duration
- Amount paid
- Purchase date

---

# 21. One-Month Trial

## 21.1 Requirement

The Super Admin can grant one month of trial access.

Example:

Trial Start:

01-Sep-2026

Trial Expiry:

30-Sep-2026

## 21.2 Rules

1. Trial must automatically expire after the trial period.
2. Trial status must be clearly displayed.
3. Trial should be distinguishable from a paid subscription.
4. Super Admin can monitor trial companies.

---

# 22. Annual Subscription

## 22.1 Requirement

After a company purchases an annual subscription, the Super Admin can activate the company's subscription.

Example:

Company:

ABC Traders

Plan:

Annual

Start:

01-Sep-2026

Expiry:

31-Aug-2027

Amount:

₹12,000

Purchase Date:

01-Sep-2026

---

# 23. Subscription Price Recording

## 23.1 Requirement

The Super Admin must be able to record the amount paid by each company.

Example:

Company:

ABC Traders

Subscription:

Annual

Amount Paid:

₹12,000

Purchase Date:

01-Sep-2026

This information is for platform-level management and reporting.

---

# 24. Subscription Extension

## 24.1 Requirement

The Super Admin can give additional subscription time to a company.

Example:

Current expiry:

31-Aug-2027

Additional period:

3 months

New expiry:

30-Nov-2027

## 24.2 Important Rule

If the company is still active, additional time must be added after the existing expiry date.

The remaining subscription period must not be lost.

## 24.3 Example

Existing:

01-Sep-2026 → 31-Aug-2027

Extension:

3 months

New:

01-Sep-2026 → 30-Nov-2027

If the company has already expired, the Super Admin can activate a new subscription from the new activation date.

---

# 25. Company Staff Users

## 25.1 Requirement

Company Admin can create staff users.

Example:

COMPANY ADMIN

    Staff 1
    Staff 2
    Staff 3

## 25.2 Staff Access Options

When creating a staff user, the Company Admin can assign:

1. Purchase Access
2. Sales Access

## 25.3 Purchase Access

If Purchase Access is enabled, the staff user can access authorized purchase functionality.

Example:

[✓] Purchase Access

## 25.4 Sales Access

If Sales Access is enabled, the staff user can access authorized sales functionality.

Example:

[✓] Sales Access

## 25.5 Both Permissions

If both are selected:

[✓] Purchase Access

[✓] Sales Access

The staff user can work with both purchase and sales functionality.

---

# 26. Staff Permission Editing

## 26.1 Requirement

The Company Admin can edit staff permissions later.

Example:

Initial:

Rahul

Purchase Access: YES

Sales Access: NO

Later:

Purchase Access: YES

Sales Access: YES

The new permission configuration must be saved and enforced by the backend.

## 26.2 Staff Management

Company Admin should be able to:

- Create staff
- View staff
- Edit staff
- Change permissions
- Disable staff

Permission changes must be audited.

---

# 27. Subscription Expiry Warning

## 27.1 Requirement

If a company's subscription is approaching expiry, a warning must be visible to the Company Admin and other authorized admin users.

The warning should be shown during the final month.

## 27.2 Example

Subscription expiry:

30-Sep-2026

Warning:

WARNING: Your annual subscription will expire soon. Please purchase/renew your annual subscription to continue using the service.

## 27.3 Warning Levels

More than 30 days:

No warning.

30 days or less:

Subscription expires soon.

7 days or less:

Subscription expires in 7 days.

Expired:

Subscription expired.

## 27.4 UI Example

--------------------------------------------

WARNING: SUBSCRIPTION EXPIRING SOON

Your company subscription expires on:

30-Sep-2026

Please purchase/renew your annual subscription
to continue using the service.

[Renew Subscription]

--------------------------------------------

---

# 28. Subscription Expiry

## 28.1 Requirement

The system must identify expired companies automatically based on the subscription expiry date.

Status:

EXPIRED

## 28.2 Company User Behavior

When the subscription expires, company users should receive a clear renewal message.

Example:

Your subscription has expired.

Please contact the administrator or renew your annual subscription to continue using the service.

## 28.3 Super Admin Behavior

Super Admin must still be able to:

- View expired company
- Renew company
- Extend company
- Update subscription
- View payment information

---

# 29. Multi-Company Data Isolation

## 29.1 Requirement

Each company must have isolated business data.

Company A must never access Company B's:

- Products
- Categories
- Suppliers
- Retailers
- Purchases
- Sales
- Inventory
- Payments
- Users
- Reports

## 29.2 Example

Company A:

ABC Traders

Product:

ABC-001

Company B must not be able to access ABC-001.

## 29.3 Backend Enforcement

Company isolation must be enforced at the backend/service/database level.

Changing an ID in a frontend request must never allow access to another company's records.

---

# 30. Authorization Hierarchy

The final authorization structure is:

SUPER_ADMIN

    |
    +---- COMPANY A
    |       |
    |       +---- COMPANY ADMIN
    |       |
    |       +---- STAFF
    |              |
    |              +---- PURCHASE ACCESS
    |              +---- SALES ACCESS
    |
    +---- COMPANY B
            |
            +---- COMPANY ADMIN
            |
            +---- STAFF

## 30.1 SUPER_ADMIN

Platform-level access.

## 30.2 COMPANY_ADMIN

Full company-level administrative access.

## 30.3 STAFF

Access based on assigned permissions.

---

# 31. New Permissions

## 31.1 Super Admin Permissions

- COMPANY_CREATE
- COMPANY_VIEW
- COMPANY_UPDATE
- COMPANY_SUSPEND
- SUBSCRIPTION_VIEW
- SUBSCRIPTION_CREATE
- SUBSCRIPTION_EXTEND
- TRIAL_GRANT

## 31.2 Company Admin Permissions

- STAFF_CREATE
- STAFF_VIEW
- STAFF_UPDATE
- STAFF_DISABLE
- PURCHASE_ACCESS
- SALES_ACCESS

## 31.3 Existing Business Permissions

The existing product, purchase, sales, inventory, payment and reporting permissions should continue to be used where applicable.

---

# 32. Product APIs

POST /api/products

GET /api/products

GET /api/products/{id}

PUT /api/products/{id}

The product API should support:

- Name
- SKU
- Category
- Subcategory
- Unit
- Current selling price
- Other existing product fields

---

# 33. Category APIs

POST /api/categories

GET /api/categories

GET /api/categories/{id}/subcategories

PUT /api/categories/{id}

The backend must validate category/subcategory relationships.

---

# 34. Party APIs

POST /api/parties

GET /api/parties/{id}

PUT /api/parties/{id}

Party information should support:

- Primary phone
- Secondary phone
- Other existing party fields

---

# 35. Bill APIs

Sales:

GET /api/sales/bills/{id}

GET /api/sales/bills/{id}/pdf

Purchase:

GET /api/purchases/bills/{id}

GET /api/purchases/bills/{id}/pdf

---

# 36. Transaction Cancellation APIs

Sales:

POST /api/sales/bills/{id}/cancel

Purchase:

POST /api/purchases/bills/{id}/cancel

Request:

{
    "reason": "Wrong quantity entered"
}

The reason must be mandatory.

---

# 37. Super Admin APIs

POST /api/super-admin/companies

GET /api/super-admin/companies

GET /api/super-admin/companies/{id}

POST /api/super-admin/companies/{id}/trial

POST /api/super-admin/companies/{id}/subscription

POST /api/super-admin/companies/{id}/extend

GET /api/super-admin/companies/{id}/subscription

---

# 38. Staff APIs

POST /api/company/users

GET /api/company/users

GET /api/company/users/{id}

PUT /api/company/users/{id}

PUT /api/company/users/{id}/permissions

Example:

{
    "purchaseAccess": true,
    "salesAccess": true
}

---

# 39. Database Changes

## 39.1 Product

product

- id
- name
- sku
- category_id
- subcategory_id
- unit
- current_selling_price
- hsn_code
- tax_rate
- description
- status
- created_on
- modified_on
- created_by
- modified_by
- version

Constraints:

- SKU UNIQUE
- category_id NOT NULL
- unit NOT NULL
- current_selling_price >= 0
- subcategory_id NULLABLE

---

# 40. Product Cost / Price History

Recommended entity:

product_cost_layer

Fields:

- id
- product_id
- purchase_bill_item_id
- buying_price
- quantity_received
- quantity_remaining
- created_on
- created_by
- status

This allows multiple buying prices for the same product.

Example:

product:

Coca Cola 500ml

cost layer 1:

₹20 / 100 units

cost layer 2:

₹22 / 100 units

---

# 41. Party Database Changes

party

- id
- name
- party_type
- primary_phone
- secondary_phone
- email
- gst_number
- tax_type
- status
- other existing fields

---

# 42. Company Database

company

- id
- company_name
- company_code
- status
- joining_date
- created_on
- modified_on
- created_by
- modified_by
- version

---

# 43. Subscription Database

subscription

- id
- company_id
- subscription_type
- start_date
- expiry_date
- amount
- purchase_date
- status
- granted_by
- created_on
- modified_on
- version

---

# 44. Company User Database

company_user

- id
- company_id
- user_id
- role_id
- status
- created_on
- modified_on
- created_by
- modified_by
- version

The existing user/role/permission structure should be extended rather than replaced.

---

# 45. Recommended Cycle 2 Development Phases

## Phase 2.1 — Product

- Unit management
- Category
- Subcategory
- Unique product code
- Selling price
- Selling price editing
- Multiple buying prices
- Cost layers
- Historical price protection

## Phase 2.2 — Party

- Primary contact
- Secondary contact
- Factory/supplier contact
- Retailer/shop contact

## Phase 2.3 — Sales

- Cost comparison
- Below-cost warning
- Red warning state
- Below-cost permission

## Phase 2.4 — Billing

- Sales bill
- Purchase bill
- Print
- PDF
- Mobile bill
- Share functionality
- WhatsApp-friendly sharing

## Phase 2.5 — Transaction Correction

- Cancellation
- Reversal
- Inventory reversal
- Payment reversal
- Cancellation reason
- Audit log
- Permission control

## Phase 2.6 — Responsive UI

- Desktop
- Tablet
- Mobile
- Mobile browser
- Responsive forms
- Responsive tables
- Mobile bill
- Mobile sharing

## Phase 2.7 — Super Admin

- Initial Super Admin
- Company creation
- Company dashboard
- Company status
- Joining date
- Subscription dates

## Phase 2.8 — Subscription

- Trial
- Annual subscription
- Amount paid
- Subscription expiry
- Subscription extension
- Expired status
- Renewal handling

## Phase 2.9 — Company Users

- Company Admin
- Staff creation
- Purchase Access
- Sales Access
- Both Access
- Permission editing
- Staff disabling

## Phase 2.10 — Testing

Test:

- Product unit
- Category
- Subcategory
- Duplicate SKU
- Multiple buying prices
- New selling price
- Historical prices
- Below-cost sales
- Secondary contacts
- Sales bill
- Purchase bill
- PDF
- Printing
- Mobile UI
- WhatsApp sharing
- Transaction cancellation
- Inventory reversal
- Super Admin
- Company creation
- Trial
- Annual subscription
- Subscription extension
- Expiry warning
- Expired company
- Staff permissions
- Multi-company data isolation

---

# 46. Final Cycle 2 Business Rules

1. Every product must have a unit.
2. Unit must be selected from a predefined list.
3. Every product must have a category.
4. Subcategory is optional.
5. Subcategory must belong to the selected category.
6. Every product must have a unique product code/SKU.
7. Barcode is not required.
8. Product selling price can be edited.
9. Editing current selling price must not change historical sales.
10. The same product can have multiple buying prices.
11. New buying prices must create new cost layers/batches.
12. Historical buying prices must never be overwritten.
13. New purchases can have a new selling price.
14. Current selling price should be used as the default for future sales.
15. Historical bills must retain their original prices.
16. Suppliers/factories must support secondary contacts.
17. Shops/retailers must support secondary contacts.
18. Selling below applicable cost must show a warning.
19. Below-cost sales can be permission controlled.
20. Sales bills must be printable.
21. Purchase bills must be printable.
22. Bills must support PDF generation.
23. Bills must be easy to share through WhatsApp.
24. Completed financial history must not be physically deleted.
25. Mistaken transactions must use cancellation/reversal.
26. Cancellation/reversal must require a reason.
27. Inventory and payment effects must be reversed where applicable.
28. Cancellation/reversal must be audited.
29. The application must work on desktop, tablet and mobile browsers.
30. Initial system administrator must be SUPER_ADMIN.
31. Super Admin can create companies.
32. Each company has a Company Admin.
33. Company Admin can create staff users.
34. Staff can receive Purchase Access.
35. Staff can receive Sales Access.
36. Staff can receive both permissions.
37. Company Admin can edit staff permissions later.
38. Super Admin can see all companies.
39. Super Admin can see company joining dates.
40. Super Admin can see subscription expiry dates.
41. Super Admin can grant one-month trial access.
42. Super Admin can activate annual subscriptions.
43. Super Admin can record the amount paid by each company.
44. Super Admin can extend subscriptions.
45. Subscription extensions must preserve remaining subscription time.
46. Company expiry must be tracked automatically.
47. Admin users must receive an expiry warning during the final month.
48. Stronger expiry warnings should appear during the final seven days.
49. Expired companies must be marked EXPIRED.
50. Expired company users must receive a renewal message/restriction according to subscription policy.
51. Super Admin can manage expired companies.
52. Company data must be isolated from other companies.
53. Backend authorization must enforce company isolation.
54. Backend authorization must enforce staff permissions.
55. Permission changes must be audited.
56. Existing architecture and service boundaries should not be unnecessarily redesigned.
57. Historical financial and inventory records must remain auditable.
58. All important business rules must be enforced on the backend.
59. APIs must validate authorization and company ownership.
60. Database constraints must enforce critical uniqueness and integrity rules.