use base64::{engine::general_purpose::STANDARD as BASE64, Engine};
use chrono::Utc;
use hmac::{Hmac, Mac};
use reqwest::header::{HeaderMap, HeaderValue, ACCEPT, AUTHORIZATION, CONTENT_TYPE};
use reqwest::Client;
use serde::Serialize;
use serde_json::{json, Value};
use sha2::Sha256;

type HmacSha256 = Hmac<Sha256>;

#[derive(Clone)]
pub struct CosmosDbClient {
    client: Client,
    endpoint: String,
    key: Vec<u8>,
    database: String,
}

#[derive(Debug)]
pub struct CosmosResponse {
    pub status: u16,
    pub body: Value,
    pub etag: Option<String>,
}

impl CosmosDbClient {
    pub fn new(endpoint: &str, key: &str, database: &str) -> Self {
        // Build reqwest client that accepts invalid TLS certs (for emulator)
        let client = Client::builder()
            .danger_accept_invalid_certs(true)
            .build()
            .expect("Failed to build HTTP client");

        let key_bytes = BASE64.decode(key).expect("Invalid base64 key");

        Self {
            client,
            endpoint: endpoint.trim_end_matches('/').to_string(),
            key: key_bytes,
            database: database.to_string(),
        }
    }

    fn generate_auth_token(
        &self,
        verb: &str,
        resource_type: &str,
        resource_link: &str,
        date: &str,
    ) -> String {
        let payload = format!(
            "{}\n{}\n{}\n{}\n\n",
            verb.to_lowercase(),
            resource_type.to_lowercase(),
            resource_link,
            date.to_lowercase()
        );

        let mut mac =
            HmacSha256::new_from_slice(&self.key).expect("HMAC can take key of any size");
        mac.update(payload.as_bytes());
        let signature = BASE64.encode(mac.finalize().into_bytes());

        let token = format!("type=master&ver=1.0&sig={}", signature);
        urlencoding::encode(&token).to_string()
    }

    fn build_headers(
        &self,
        verb: &str,
        resource_type: &str,
        resource_link: &str,
        extra_headers: Option<Vec<(&str, &str)>>,
    ) -> HeaderMap {
        let date = Utc::now().format("%a, %d %b %Y %H:%M:%S GMT").to_string();
        let auth = self.generate_auth_token(verb, resource_type, resource_link, &date);

        let mut headers = HeaderMap::new();
        headers.insert(AUTHORIZATION, HeaderValue::from_str(&auth).unwrap());
        headers.insert("x-ms-date", HeaderValue::from_str(&date).unwrap());
        headers.insert("x-ms-version", HeaderValue::from_static("2018-12-31"));
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));
        headers.insert(ACCEPT, HeaderValue::from_static("application/json"));

        if let Some(extra) = extra_headers {
            for (k, v) in extra {
                headers.insert(
                    reqwest::header::HeaderName::from_bytes(k.as_bytes()).unwrap(),
                    HeaderValue::from_str(v).unwrap(),
                );
            }
        }

        headers
    }

    /// Ensure database and containers exist
    pub async fn ensure_database_and_containers(&self) -> Result<(), String> {
        // Create database
        let date = Utc::now().format("%a, %d %b %Y %H:%M:%S GMT").to_string();
        let auth = self.generate_auth_token("post", "dbs", "", &date);
        let mut headers = HeaderMap::new();
        headers.insert(AUTHORIZATION, HeaderValue::from_str(&auth).unwrap());
        headers.insert("x-ms-date", HeaderValue::from_str(&date).unwrap());
        headers.insert("x-ms-version", HeaderValue::from_static("2018-12-31"));
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));

        let _ = self
            .client
            .post(format!("{}/dbs", self.endpoint))
            .headers(headers)
            .json(&json!({ "id": self.database }))
            .send()
            .await
            .map_err(|e| e.to_string())?;

        // Create containers
        let containers = vec![
            ("players", "/playerId"),
            ("scores", "/playerId"),
            ("leaderboards", "/region"),
        ];

        for (name, pk) in containers {
            let resource_link = format!("dbs/{}", self.database);
            let date = Utc::now().format("%a, %d %b %Y %H:%M:%S GMT").to_string();
            let auth = self.generate_auth_token("post", "colls", &resource_link, &date);
            let mut headers = HeaderMap::new();
            headers.insert(AUTHORIZATION, HeaderValue::from_str(&auth).unwrap());
            headers.insert("x-ms-date", HeaderValue::from_str(&date).unwrap());
            headers.insert("x-ms-version", HeaderValue::from_static("2018-12-31"));
            headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));

            let _ = self
                .client
                .post(format!("{}/dbs/{}/colls", self.endpoint, self.database))
                .headers(headers)
                .json(&json!({
                    "id": name,
                    "partitionKey": {
                        "paths": [pk],
                        "kind": "Hash",
                        "version": 2
                    }
                }))
                .send()
                .await
                .map_err(|e| e.to_string())?;
        }

        Ok(())
    }

    /// Create a document
    pub async fn create_document<T: Serialize>(
        &self,
        collection: &str,
        partition_key: &str,
        document: &T,
    ) -> Result<CosmosResponse, String> {
        let resource_link = format!("dbs/{}/colls/{}", self.database, collection);
        let headers = self.build_headers("post", "docs", &resource_link, Some(vec![
            ("x-ms-documentdb-partitionkey", &format!("[\"{}\"]", partition_key)),
        ]));

        let url = format!("{}/{}/docs", self.endpoint, resource_link);
        let resp = self
            .client
            .post(&url)
            .headers(headers)
            .json(document)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        let status = resp.status().as_u16();
        let etag = resp.headers().get("etag").map(|v| v.to_str().unwrap_or("").to_string());
        let body: Value = resp.json().await.map_err(|e| e.to_string())?;

        Ok(CosmosResponse { status, body, etag })
    }

    /// Read a document by id and partition key (point read)
    pub async fn read_document(
        &self,
        collection: &str,
        doc_id: &str,
        partition_key: &str,
    ) -> Result<CosmosResponse, String> {
        let resource_link = format!(
            "dbs/{}/colls/{}/docs/{}",
            self.database, collection, doc_id
        );
        let headers = self.build_headers("get", "docs", &resource_link, Some(vec![
            ("x-ms-documentdb-partitionkey", &format!("[\"{}\"]", partition_key)),
        ]));

        let url = format!("{}/{}", self.endpoint, resource_link);
        let resp = self
            .client
            .get(&url)
            .headers(headers)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        let status = resp.status().as_u16();
        let etag = resp.headers().get("etag").map(|v| v.to_str().unwrap_or("").to_string());
        let body: Value = resp.json().await.map_err(|e| e.to_string())?;

        Ok(CosmosResponse { status, body, etag })
    }

    /// Replace (full update) a document
    pub async fn replace_document<T: Serialize>(
        &self,
        collection: &str,
        doc_id: &str,
        partition_key: &str,
        document: &T,
        if_match: Option<&str>,
    ) -> Result<CosmosResponse, String> {
        let resource_link = format!(
            "dbs/{}/colls/{}/docs/{}",
            self.database, collection, doc_id
        );
        let mut extra = vec![
            ("x-ms-documentdb-partitionkey", format!("[\"{}\"]", partition_key)),
        ];
        if let Some(etag) = if_match {
            extra.push(("if-match", etag.to_string()));
        }

        let extra_refs: Vec<(&str, &str)> = extra.iter().map(|(k, v)| (*k, v.as_str())).collect();
        let headers = self.build_headers("put", "docs", &resource_link, Some(extra_refs));

        let url = format!("{}/{}", self.endpoint, resource_link);
        let resp = self
            .client
            .put(&url)
            .headers(headers)
            .json(document)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        let status = resp.status().as_u16();
        let etag_resp = resp.headers().get("etag").map(|v| v.to_str().unwrap_or("").to_string());
        let body: Value = resp.json().await.map_err(|e| e.to_string())?;

        Ok(CosmosResponse {
            status,
            body,
            etag: etag_resp,
        })
    }

    /// Delete a document
    pub async fn delete_document(
        &self,
        collection: &str,
        doc_id: &str,
        partition_key: &str,
    ) -> Result<u16, String> {
        let resource_link = format!(
            "dbs/{}/colls/{}/docs/{}",
            self.database, collection, doc_id
        );
        let headers = self.build_headers("delete", "docs", &resource_link, Some(vec![
            ("x-ms-documentdb-partitionkey", &format!("[\"{}\"]", partition_key)),
        ]));

        let url = format!("{}/{}", self.endpoint, resource_link);
        let resp = self
            .client
            .delete(&url)
            .headers(headers)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        Ok(resp.status().as_u16())
    }

    /// Query documents with SQL
    pub async fn query_documents(
        &self,
        collection: &str,
        query: &str,
        parameters: Vec<Value>,
        partition_key: Option<&str>,
    ) -> Result<Vec<Value>, String> {
        let resource_link = format!("dbs/{}/colls/{}", self.database, collection);

        let mut extra = vec![
            ("x-ms-documentdb-isquery", "true".to_string()),
            ("content-type", "application/query+json".to_string()),
        ];
        if let Some(pk) = partition_key {
            extra.push((
                "x-ms-documentdb-partitionkey",
                format!("[\"{}\"]", pk),
            ));
        } else {
            extra.push(("x-ms-documentdb-query-enablecrosspartition", "true".to_string()));
        }

        let extra_refs: Vec<(&str, &str)> = extra.iter().map(|(k, v)| (*k, v.as_str())).collect();
        let headers = self.build_headers("post", "docs", &resource_link, Some(extra_refs));

        let url = format!("{}/{}/docs", self.endpoint, resource_link);
        let body = json!({
            "query": query,
            "parameters": parameters
        });

        let resp = self
            .client
            .post(&url)
            .headers(headers)
            .json(&body)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        let status = resp.status().as_u16();
        let resp_body: Value = resp.json().await.map_err(|e| e.to_string())?;

        if status >= 400 {
            return Err(format!("Query failed ({}): {}", status, resp_body));
        }

        let documents = resp_body["Documents"]
            .as_array()
            .cloned()
            .unwrap_or_default();

        Ok(documents)
    }

    /// Upsert a document
    pub async fn upsert_document<T: Serialize>(
        &self,
        collection: &str,
        partition_key: &str,
        document: &T,
    ) -> Result<CosmosResponse, String> {
        let resource_link = format!("dbs/{}/colls/{}", self.database, collection);
        let headers = self.build_headers("post", "docs", &resource_link, Some(vec![
            ("x-ms-documentdb-partitionkey", &format!("[\"{}\"]", partition_key)),
            ("x-ms-documentdb-is-upsert", "true"),
        ]));

        let url = format!("{}/{}/docs", self.endpoint, resource_link);
        let resp = self
            .client
            .post(&url)
            .headers(headers)
            .json(document)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        let status = resp.status().as_u16();
        let etag = resp.headers().get("etag").map(|v| v.to_str().unwrap_or("").to_string());
        let body: Value = resp.json().await.map_err(|e| e.to_string())?;

        Ok(CosmosResponse { status, body, etag })
    }
}
