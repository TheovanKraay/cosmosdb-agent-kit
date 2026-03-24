# E-Commerce Order API - Python/FastAPI

Azure Cosmos DB order management API demonstrating best practices.

## Setup

1. Copy `.env.example` to `.env` and configure your Cosmos DB settings
2. Create virtual environment:
   ```bash
   python -m venv .venv
   .venv\Scripts\activate  # Windows
   # or
   source .venv/bin/activate  # Linux/Mac
   ```
3. Install dependencies:
   ```bash
   pip install -e .
   ```

## Running

```bash
uvicorn app.main:app --reload --port 8000
```

## API Endpoints

- `POST /api/orders` - Create order
- `GET /api/orders/{id}?customerId={customerId}` - Get order by ID
- `GET /api/orders/customer/{customerId}` - Get customer orders
- `PATCH /api/orders/{id}/status?customerId={customerId}` - Update status
- `GET /api/orders/status/{status}` - Get orders by status
- `GET /api/orders/daterange?startDate={date}&endDate={date}` - Date range query

## Cosmos DB Rules Applied

- **Rule 4.1**: Singleton CosmosClient
- **Rule 4.10**: Enum string serialization (`class OrderStatus(str, Enum)`)
- **Rule 1.1**: Embedded order items
- **Rule 2.1**: High-cardinality partition key (customerId)
- **Rule 3.1**: Single-partition queries where possible
- **Rule 3.5**: Parameterized queries
