# OhDomi Spring API

REST API for the OhDomi backend. Unless the application configuration overrides the
server port, the local base URL is:

```text
http://localhost:8080
```

All request and response bodies use JSON. Dates use `YYYY-MM-DD`, times use
`HH:mm:ss`, and date-times use ISO-8601 format.

## Endpoint summary

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/test` | Check whether the server is running |
| `POST` | `/api/auth/login` | Log in as an owner or administrator |
| `POST` | `/api/auth/register` | Register an owner account |
| `GET` | `/api/stores` | List stores |
| `POST` | `/api/stores` | Create a store |
| `GET` | `/api/stores/{storeId}` | Get one store |
| `PUT` | `/api/stores/{storeId}` | Replace a store's profile information |
| `GET` | `/api/stores/{storeId}/staff` | Get a store's staff shifts |
| `POST` | `/api/stores/{storeId}/staff` | Upload a staff shift |
| `GET` | `/api/stores/{storeId}/facilities` | Get a store's facilities and latest checks |
| `POST` | `/api/stores/{storeId}/facilities` | Create a facility |
| `POST` | `/api/stores/{storeId}/facilities/{facilityId}/checks` | Upload a facility check |
| `GET` | `/api/stores/{storeId}/sales-summary` | Get sales totals for a date range |
| `GET` | `/api/stores/{storeId}/inventory` | Get inventory items |
| `POST` | `/api/stores/{storeId}/inventory` | Create an inventory item |
| `PUT` | `/api/stores/{storeId}/inventory/{inventoryItemId}` | Replace an inventory item |
| `POST` | `/api/stores/{storeId}/customer-orders` | Upload a customer order and its items |
| `GET` | `/api/stores/{storeId}/order-recommendations` | Get ordering recommendations |
| `POST` | `/api/stores/{storeId}/order-recommendations` | Upload an ordering recommendation |
| `GET` | `/api/stores/{storeId}/purchase-orders` | Get purchase orders |
| `POST` | `/api/stores/{storeId}/purchase-orders` | Upload a purchase order and its items |
| `GET` | `/api/hygiene-inspections` | List hygiene inspections |
| `POST` | `/api/hygiene-inspections` | Upload an inspection and its related information |
| `GET` | `/api/hygiene-inspections/{inspectionId}` | Get inspection details |
| `GET` | `/api/risk-assessments/latest` | Get the latest store risk assessments |
| `POST` | `/api/risk-assessments` | Upload a store risk assessment |
| `GET` | `/api/board/posts` | List notice or inquiry posts |
| `GET` | `/api/board/posts/{postId}` | Get one board post |
| `POST` | `/api/board/posts` | Create a notice or inquiry |
| `PATCH` | `/api/board/posts/{postId}/pin` | Toggle a post's pinned status |
| `POST` | `/api/board/posts/{postId}/answer` | Create or replace an inquiry answer |
| `GET` | `/api/ui/stores/{storeId}/overview` | Get all owner dashboard data |
| `GET` | `/api/ui/stores/{storeId}/management` | Get owner management-page data |
| `GET` | `/api/ui/stores/{storeId}/hygiene` | Get owner hygiene-page data |
| `GET` | `/api/ui/stores/{storeId}/orders` | Get owner ordering-page data |
| `GET` | `/api/ui/stores/{storeId}/sales` | Get owner sales-page data |
| `GET` | `/api/ui/admin/overview` | Get all administrator dashboard data |
| `GET` | `/api/ui/admin/stores` | Get administrator store-page data |
| `GET` | `/api/ui/admin/risks` | Get administrator risk-page data |
| `GET` | `/api/ui/admin/hygiene` | Get administrator hygiene-page data |
| `GET` | `/api/ui/admin/sales` | Get administrator sales-page data |

## Health check

### `GET /api/test`

Checks that the application is running.

Example response:

```text
OhDomi backend server is running!
```

## Authentication

The current API returns user information but does not issue a token or session.

### `POST /api/auth/login`

Request body:

```json
{
  "loginId": "owner01",
  "password": "password123",
  "role": "OWNER"
}
```

`role` must be `OWNER` or `ADMIN` (case-insensitive).

Successful response fields:

```text
userId, loginId, name, role, phone, storeId
```

Returns `401 Unauthorized` when the credentials or selected role do not match.

### `POST /api/auth/register`

Creates an `OWNER` account and returns `201 Created`.

Request body:

```json
{
  "loginId": "new.owner",
  "password": "password123",
  "name": "New Owner",
  "phone": "010-1234-5678"
}
```

Validation:

- `loginId`: 4-100 characters; letters, numbers, `.`, `_`, and `-` only
- `password`: 8-72 characters
- `name`: maximum 100 characters
- `phone`: maximum 30 characters; numbers, spaces, `+`, `(`, `)`, and `-` only

Successful response fields:

```text
userId, loginId, name, role, phone, createdAt
```

Returns `409 Conflict` when `loginId` already exists.

## Stores

### `GET /api/stores`

Returns all stores ordered by store ID.

Response fields per store:

```text
storeId, storeCode, storeName, ownerName, region, address, phone,
openTime, closeTime, operationStatus, openedOn, contractEndsOn,
monthlySalesTarget
```

### `GET /api/stores/{storeId}`

Returns one store. Returns `404 Not Found` if the store does not exist.

### `POST /api/stores`

Creates a store for an active user whose role is `OWNER`. Returns `201 Created`.

Request body:

```json
{
  "ownerUserId": 2,
  "storeCode": "ST-NEW",
  "name": "New Store",
  "region": "Seoul",
  "address": "1 Store Street",
  "phone": "010-1234-5678",
  "openTime": "09:00:00",
  "closeTime": "22:00:00",
  "operationStatus": "OPEN",
  "openedOn": "2026-07-29",
  "contractEndsOn": "2028-07-28",
  "monthlySalesTarget": 50000000
}
```

Returns `409 Conflict` when `storeCode` already exists.

### `PUT /api/stores/{storeId}`

Replaces the complete store profile. It uses the same body as `POST /api/stores` and
returns the updated store.

### `GET /api/stores/{storeId}/staff`

Returns staff shifts for a store and date.

Query parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `date` | No | Work date (`YYYY-MM-DD`); defaults to today |

Example:

```http
GET /api/stores/1/staff?date=2026-07-29
```

Response fields per shift:

```text
staffShiftId, name, role, workDate, startsAt, endsAt, status
```

### `POST /api/stores/{storeId}/staff`

Uploads a staff shift and returns `201 Created`.

```json
{
  "name": "Staff Name",
  "role": "CASHIER",
  "workDate": "2026-07-29",
  "startsAt": "09:00:00",
  "endsAt": "17:00:00",
  "status": "SCHEDULED"
}
```

### `GET /api/stores/{storeId}/facilities`

Returns active facilities and each facility's latest check.

Response fields per facility:

```text
facilityId, name, status, memo, checkedAt
```

### `POST /api/stores/{storeId}/facilities`

Creates a facility and returns `201 Created`. `active` defaults to `true` when it is
omitted or `null`.

```json
{
  "name": "Refrigerator",
  "active": true
}
```

Returns `409 Conflict` when the store already has a facility with the same name.

### `POST /api/stores/{storeId}/facilities/{facilityId}/checks`

Uploads a check for a facility belonging to the selected store and returns
`201 Created`.

```json
{
  "status": "NORMAL",
  "memo": "Temperature is normal",
  "checkedAt": "2026-07-29T10:00:00"
}
```

### `GET /api/stores/{storeId}/sales-summary`

Returns completed-order sales data over an inclusive date range.

Query parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `from` | Yes | Start date (`YYYY-MM-DD`) |
| `to` | Yes | End date (`YYYY-MM-DD`); must not be before `from` |

Example:

```http
GET /api/stores/1/sales-summary?from=2026-07-01&to=2026-07-31
```

Response fields:

```text
storeId, from, to, sales, orders, averageOrderAmount
```

## Inventory and ordering

### `GET /api/stores/{storeId}/inventory`

Returns the store's inventory.

Response fields per item:

```text
inventoryItemId, itemName, category, unit, currentQuantity,
reorderLevel, unitPrice, updatedAt
```

### `POST /api/stores/{storeId}/inventory`

Creates an inventory item and returns `201 Created`.

```json
{
  "itemName": "Salmon",
  "category": "SEAFOOD",
  "unit": "kg",
  "currentQuantity": 10,
  "reorderLevel": 20,
  "unitPrice": 18000
}
```

Returns `409 Conflict` when an item with the same name already exists at the store.

### `PUT /api/stores/{storeId}/inventory/{inventoryItemId}`

Replaces an inventory item using the same body as the inventory `POST` endpoint.

### `POST /api/stores/{storeId}/customer-orders`

Uploads a customer order and all of its line items in one transaction. The API
calculates `totalAmount` from each item's `quantity * unitPrice`. `channel` must be
`IN_STORE`, `DELIVERY`, or `TAKEOUT`.

```json
{
  "channel": "IN_STORE",
  "orderedAt": "2026-07-29T12:00:00",
  "status": "COMPLETED",
  "items": [
    {
      "menuItemId": 1,
      "quantity": 2,
      "unitPrice": 11000
    }
  ]
}
```

### `GET /api/stores/{storeId}/order-recommendations`

Returns recommendations for one date.

Query parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `date` | No | Recommendation date (`YYYY-MM-DD`); defaults to today |

Example:

```http
GET /api/stores/1/order-recommendations?date=2026-07-29
```

Response fields per recommendation:

```text
recommendationId, inventoryItemId, itemName, category, unit,
currentQuantity, expectedUsage, recommendedQuantity, unitPrice,
amount, riskLevel, reason, recommendationDate
```

### `POST /api/stores/{storeId}/order-recommendations`

Uploads a recommendation for an inventory item belonging to the selected store and
returns `201 Created`.

```json
{
  "inventoryItemId": 1,
  "recommendationDate": "2026-07-29",
  "expectedUsage": 18,
  "recommendedQuantity": 8,
  "riskLevel": "WARNING",
  "reason": "Stock is below expected usage"
}
```

Only one recommendation may exist for an inventory item and date.

### `GET /api/stores/{storeId}/purchase-orders`

Returns a store's purchase orders, newest first.

Response fields per order:

```text
purchaseOrderId, orderNumber, status, orderedAt, expectedAt,
totalAmount, createdAt, itemCount
```

### `POST /api/stores/{storeId}/purchase-orders`

Uploads a purchase order and all of its line items in one transaction. The API
calculates `totalAmount`. Status must be `DRAFT`, `ORDERED`, `SHIPPING`, `RECEIVED`,
or `CANCELLED`.

```json
{
  "orderNumber": "PO-20260729-001",
  "status": "DRAFT",
  "orderedAt": null,
  "expectedAt": "2026-07-31T09:00:00",
  "items": [
    {
      "inventoryItemId": 1,
      "quantity": 5,
      "unitPrice": 18000
    }
  ]
}
```

Returns `409 Conflict` when `orderNumber` already exists.

## Hygiene inspections

### `GET /api/hygiene-inspections`

Lists inspections, newest first.

Query parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `storeId` | No | Return inspections for only this store |

Example:

```http
GET /api/hygiene-inspections?storeId=1
```

Response fields per inspection:

```text
inspectionId, storeId, storeName, score, status, reviewer, summary,
inspectedAt, imageCount, openTaskCount
```

### `GET /api/hygiene-inspections/{inspectionId}`

Returns an object containing `inspection`, `checkResults`, `images`, and
`improvementTasks`. Returns `404 Not Found` if the inspection does not exist.

Check-result fields:

```text
checkResultId, itemName, score, status, memo
```

Improvement-task fields:

```text
improvementTaskId, title, description, priority, status, dueAt, completedAt
```

Image fields:

```text
imageId, imageUrl, category, analysisResult, uploadedAt
```

### `POST /api/hygiene-inspections`

Uploads a complete hygiene inspection in one transaction and returns `201 Created`.
The `checkResults`, `images`, and `improvementTasks` arrays are optional. Image
binary storage is external; `imageUrl` points to the uploaded image.

```json
{
  "storeId": 1,
  "score": 85,
  "status": "WARNING",
  "reviewer": "Inspector Name",
  "summary": "Entrance needs cleaning",
  "inspectedAt": "2026-07-29T11:30:00",
  "checkResults": [
    {
      "itemName": "Entrance cleanliness",
      "score": 70,
      "status": "WARNING",
      "memo": "Clean before opening"
    }
  ],
  "images": [
    {
      "imageUrl": "/uploads/hygiene/inspection.jpg",
      "category": "ENTRANCE",
      "analysisResult": "Cleaning required"
    }
  ],
  "improvementTasks": [
    {
      "title": "Clean entrance",
      "description": "Clean and upload a new photo",
      "priority": "WARNING",
      "status": "OPEN",
      "dueAt": "2026-07-30T09:00:00",
      "completedAt": null
    }
  ]
}
```

## Risk assessments

### `GET /api/risk-assessments/latest`

Returns the latest assessment for every store, ordered by risk score descending.

Query parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `level` | No | Filter by risk level (converted to uppercase) |

Example:

```http
GET /api/risk-assessments/latest?level=HIGH
```

Response fields per assessment:

```text
riskAssessmentId, storeId, storeName, ownerName, region, riskScore,
riskLevel, salesChangeRate, hygieneScore, delayedOrderCount,
complaintCount, mainReason, prediction, recommendedAction, assessedAt
```

### `POST /api/risk-assessments`

Uploads a store risk assessment and returns `201 Created`. `riskScore` and
`hygieneScore` must be between 0 and 100.

```json
{
  "storeId": 1,
  "riskScore": 65.5,
  "riskLevel": "WARNING",
  "salesChangeRate": -3.2,
  "hygieneScore": 85,
  "delayedOrderCount": 1,
  "complaintCount": 2,
  "mainReason": "Sales decreased",
  "prediction": "Risk may increase",
  "recommendedAction": "Review store operations",
  "assessedAt": "2026-07-29T12:00:00"
}
```

## Board

Board types are `NOTICE` (announcements) and `INQUIRY` (questions).

### `GET /api/board/posts`

Lists posts with pinned posts first, followed by newest posts.

Query parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `boardType` | No | `NOTICE` or `INQUIRY`; defaults to `NOTICE` |

Examples:

```http
GET /api/board/posts?boardType=NOTICE
GET /api/board/posts?boardType=INQUIRY
```

Response fields per post:

```text
postId, boardType, category, title, content, authorName, storeId,
status, isPinned, isUrgent, viewCount, createdAt, updatedAt, answer
```

### `GET /api/board/posts/{postId}`

Returns one post and increments its `viewCount`. Returns `404 Not Found` when the
post does not exist.

### `POST /api/board/posts`

Creates a notice or inquiry. The referenced user must be active, and `storeId`,
when supplied, must exist. Only a user whose database role is `ADMIN` can create a
`NOTICE`.

Request body:

```json
{
  "authorUserId": 1,
  "storeId": null,
  "boardType": "NOTICE",
  "category": "Announcement",
  "title": "New announcement",
  "content": "Announcement content",
  "isPinned": false,
  "isUrgent": false
}
```

`authorUserId` must be positive, `storeId` may be `null`, `category` has a maximum
length of 50, and `title` has a maximum length of 200. New notices receive status
`PUBLISHED`; new inquiries receive status `PENDING`.

### `PATCH /api/board/posts/{postId}/pin`

Toggles `isPinned` and returns the updated post. This endpoint has no request body.

### `POST /api/board/posts/{postId}/answer`

Creates or replaces the answer to an `INQUIRY` and changes the post status to
`ANSWERED`. The author must have the `ADMIN` database role.

Request body:

```json
{
  "authorUserId": 1,
  "content": "Administrator answer"
}
```

## UI aggregation endpoints

These endpoints return page-oriented maps assembled from several database queries.
Their response shapes are intended for the current frontend dashboards.

### Owner/store UI

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/ui/stores/{storeId}/overview` | Combined management, hygiene, ordering, and sales data |
| `GET` | `/api/ui/stores/{storeId}/management` | Store details, facilities, staff, and alerts |
| `GET` | `/api/ui/stores/{storeId}/hygiene` | Latest inspection, checklist, improvements, and inspection history |
| `GET` | `/api/ui/stores/{storeId}/orders` | Recommendations, order history, and ordering summary |
| `GET` | `/api/ui/stores/{storeId}/sales` | Sales summary, hourly sales, channels, and menu ranking |

### Administrator UI

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/ui/admin/overview` | Combined stores, hygiene, sales, and risk data |
| `GET` | `/api/ui/admin/stores` | Store list, contract information, sales, hygiene, and risk summary |
| `GET` | `/api/ui/admin/risks` | Latest risk assessments and risk summary |
| `GET` | `/api/ui/admin/hygiene` | Latest store inspections and hygiene summary |
| `GET` | `/api/ui/admin/sales` | Sales totals, ranking, regional totals, and weak-store data |

## Error responses

Handled API errors use this structure:

```json
{
  "timestamp": "2026-07-29T06:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Store 999 was not found"
}
```

Common status codes:

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Invalid parameters, request body, or validation failure |
| `401 Unauthorized` | Invalid login credentials or role |
| `404 Not Found` | Requested database resource does not exist |
| `409 Conflict` | Registration uses an existing login ID |
