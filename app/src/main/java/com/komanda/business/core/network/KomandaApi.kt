package com.komanda.business.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface KomandaApi {

    @Headers("Content-Type: application/json")
    @POST("/api/v1/auth/mobile/sessions")
    suspend fun mobileLogin(
        @Body body: MobileLoginRequest
    ): Response<MobileLoginResponse>

    @GET("/api/v1/auth/mobile/context")
    suspend fun getMobileContext(
        @Header("Authorization") authHeader: String? = null
    ): Response<MobileContextResponse>

    @GET("/api/v1/tenants/{tenantId}/orders")
    suspend fun listOrders(
        @Path("tenantId") tenantId: String,
        @Query("status") status: String? = null,
        @Query("cursor") cursor: String? = null
    ): Response<TenantOrdersListResponse>

    @GET("/api/v1/tenants/{tenantId}/orders/{orderId}")
    suspend fun getOrder(
        @Path("tenantId") tenantId: String,
        @Path("orderId") orderId: String
    ): Response<TenantOrderResponse>

    @Headers("Content-Type: application/merge-patch+json")
    @PATCH("/api/v1/tenants/{tenantId}/orders/{orderId}")
    suspend fun transitionOrderStatus(
        @Path("tenantId") tenantId: String,
        @Path("orderId") orderId: String,
        @Header("If-Match") version: String,
        @Body body: TransitionOrderRequest
    ): Response<TenantOrderResponse>

    @Headers("Content-Type: application/json")
    @POST("/api/v1/tenants/{tenantId}/orders/from-items")
    suspend fun createDirectOrder(
        @Path("tenantId") tenantId: String,
        @Header("idempotency-key") idempotencyKey: String,
        @Body body: CreateDirectOrderRequest
    ): Response<TenantOrderResponse>

    @GET("/api/v1/tenants/{tenantId}/catalog/categories")
    suspend fun listCategories(
        @Path("tenantId") tenantId: String
    ): Response<CatalogResponse<CatalogCategoryDto>>

    @GET("/api/v1/tenants/{tenantId}/catalog/items")
    suspend fun listItems(
        @Path("tenantId") tenantId: String
    ): Response<CatalogResponse<CatalogItemDto>>

    @POST("/api/v1/tenants/{tenantId}/orders/{orderId}/invoice")
    suspend fun createInvoice(
        @Path("tenantId") tenantId: String,
        @Path("orderId") orderId: String,
        @Body body: CreateInvoiceRequest
    ): Response<InvoiceResponse>
}
