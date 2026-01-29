import ssl
import urllib3
urllib3.disable_warnings()

# Check environment variables that might be interfering
import os
print('Environment check:')
print(f'  COSMOS_ENDPOINT from env: {os.getenv("COSMOS_ENDPOINT", "not set")}')
print(f'  COSMOS_DATABASE from env: {os.getenv("COSMOS_DATABASE", "not set")}')
print()

# Force use emulator values
endpoint = 'https://localhost:8081'
key = 'C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw=='

print(f'Using endpoint: {endpoint}')
print()

from azure.cosmos import CosmosClient, PartitionKey

# Create client
client = CosmosClient(endpoint, key)

# Create database
print('Creating database testdb...')
db = client.create_database_if_not_exists('testdb')
print(f'Database created: {db.id}')

# Create container - try direct create
print('Creating container testcontainer...')
try:
    container = db.create_container(
        id='testcontainer',
        partition_key=PartitionKey(path='/pk')
    )
    print(f'Container created: {container.id}')
except Exception as e:
    print(f'Create failed: {e}')
    print('Trying to get existing container...')
    container = db.get_container_client('testcontainer')

# Test write
print('Testing write...')
item = {'id': 'test1', 'pk': 'test', 'name': 'Hello World'}
result = container.upsert_item(item)
print(f'Item created: {result}')

print('SUCCESS!')
