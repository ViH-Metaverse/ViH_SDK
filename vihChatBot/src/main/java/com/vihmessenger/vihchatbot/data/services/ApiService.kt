package com.vihmessenger.vihchatbot.data.services

import com.vihmessenger.vihchatbot.constants.BaseAPIConstants
import com.vihmessenger.vihchatbot.data.model.ChatHistoryModel
import com.vihmessenger.vihchatbot.data.model.ChatListModelResponse
import com.vihmessenger.vihchatbot.data.model.ChatMessageModel
import com.vihmessenger.vihchatbot.data.model.DeviceTokenRequest
import com.vihmessenger.vihchatbot.data.model.DeviceTokenResponse
import com.vihmessenger.vihchatbot.data.model.EnterPriseDiscoverModel
import com.vihmessenger.vihchatbot.data.model.EnterpriseApiResponse
import com.vihmessenger.vihchatbot.data.model.CallDetailsRequest
import com.vihmessenger.vihchatbot.data.model.IndustryResponse
import com.vihmessenger.vihchatbot.data.model.EmailLoginRequest
import com.vihmessenger.vihchatbot.data.model.EmailLoginResponse
import com.vihmessenger.vihchatbot.data.model.SubscribeChannelRequest
import com.vihmessenger.vihchatbot.data.model.SubscribeChannelResponse
import com.vihmessenger.vihchatbot.data.model.LoanApprovalRequest
import com.vihmessenger.vihchatbot.data.model.LoanApprovalResponse
import com.vihmessenger.vihchatbot.data.model.LogoutDataModel
import com.vihmessenger.vihchatbot.data.model.SdkFeatureResponse
import com.vihmessenger.vihchatbot.data.model.UpdateUserProfile
import com.vihmessenger.vihchatbot.data.model.UserProfilePatchUsername
import com.vihmessenger.vihchatbot.data.model.UserProfileRequest
import com.vihmessenger.vihchatbot.data.model.UserProfileResponse
import com.vihmessenger.vihchatbot.data.model.UserProfileUpdateResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import com.google.gson.JsonObject

/**
 * SECURITY: Authorization header is now injected centrally via AuthInterceptor.
 * Removed inline "Bearer ${AppController.prefs?.accessToken}" default parameters
 * which were vulnerable to race conditions and could send "Bearer null".
 */
interface ApiService : BaseApiService {

    // Trailing slash required by the DRF SimpleRouter — without it the server 301-redirects.
    @GET(BaseAPIConstants.SDK_FEATURES + "{hashCode}/")
    suspend fun getSdkFeatures(@Path("hashCode") hashCode: String): Response<SdkFeatureResponse>

    @POST(BaseAPIConstants.USER_SIGNUP_LOGIN)
    suspend fun createUserProfile(@Body body: UserProfileRequest): Response<UserProfileResponse>

    // Email-OTP token exchange. Body carries the verified Cognito ID token (auth) plus
    // mobile/channel_id for hashcode-matched delivery; returns the existing app-session
    // tokens or account_status == "needs_profile".
    @POST(BaseAPIConstants.EMAIL_LOGIN)
    suspend fun emailLogin(@Body body: EmailLoginRequest): Response<EmailLoginResponse>

    // Subscribe the authenticated user to a channel (Settings hashkey switch).
    @POST(BaseAPIConstants.SUBSCRIBE_CHANNEL)
    suspend fun subscribeChannel(@Body body: SubscribeChannelRequest): Response<SubscribeChannelResponse>

    @PATCH(BaseAPIConstants.USER_PROFILE)
    suspend fun updateUserProfile(
        @Body body: UpdateUserProfile
    ): Response<UserProfileUpdateResponse>

    @PATCH(BaseAPIConstants.USER_PROFILE)
    suspend fun updateUserProfileUsername(
        @Body body: UserProfilePatchUsername
    ): Response<UserProfileUpdateResponse>

    @Multipart
    @PATCH(BaseAPIConstants.USER_PROFILE)
    suspend fun updateUserProfileImage(
        @Part image: MultipartBody.Part
    ): Response<UserProfileUpdateResponse>

    @Multipart
    @PATCH(BaseAPIConstants.USER_PROFILE)
    suspend fun updateProfileSelective(
        @PartMap partMap: HashMap<String, RequestBody>,
        @Part user_profile_image: MultipartBody.Part?
    ): Response<UserProfileUpdateResponse>

    @GET(BaseAPIConstants.MAIN_CHAT + "{channel_id}" + "/" + "{enterprise_id}")
    suspend fun getChatResponse(
        @Path("channel_id") hashcode: String,
        @Path("enterprise_id") enterpriseId: String,
        @Query("question") question: String,
        @Query("session_id") session_id: String
    ): Response<ChatMessageModel>

    @GET(BaseAPIConstants.CHAT_HISTORY + "{channel_id}" + "/" + "{enterprise_id}")
    suspend fun getChatHistory(
        @Path("channel_id") channel_id: String,
        @Path("enterprise_id") enterprise_id: String
    ): Response<ChatHistoryModel>

    @GET(BaseAPIConstants.MAIN_CHAT_LIST)
    suspend fun getChatListResponse(
        @Query("channel_id") hashcode: String,
        @Header("Content-Type") contentTypeUrlEncoded: String = "application/x-www-form-urlencoded"
    ): Response<ChatListModelResponse>

    @GET(BaseAPIConstants.MAIN_DISCOVER_LIST)
    suspend fun getEnterpriseDiscoverListResponse(
        @Query("channel_id") hashcode: String,
        @Query("page") page: Int,
        @Query("search") search: String,
        @Query("industries") industries: String
    ): Response<EnterPriseDiscoverModel>

    @GET(BaseAPIConstants.INDUSTRIES)
    suspend fun getIndustries(): Response<IndustryResponse>

    @GET(BaseAPIConstants.ENTERPRISES)
    suspend fun getEnterprises(@Query("enterprise_id") enterprise_id: String): Response<EnterpriseApiResponse>

    @Multipart
    @PATCH(BaseAPIConstants.USER_PROFILE)
    suspend fun createProfile(
        @PartMap partMap: MutableMap<String, RequestBody>,
        @Part user_profile_image: MultipartBody.Part
    ): Response<UserProfileUpdateResponse>

    @FormUrlEncoded
    @POST(BaseAPIConstants.USER_LOGOUT)
    suspend fun userLogout(
        @Field("refresh_token") refresh_token: String
    ): Response<LogoutDataModel>

    @POST(BaseAPIConstants.REGISTER_FCM_TOKEN)
    suspend fun registerFcmToken(
        @Body body: DeviceTokenRequest
    ): Response<DeviceTokenResponse>

    @POST
    suspend fun postLoanApproval(
        @Url url: String,
        @Body body: LoanApprovalRequest
    ): Response<LoanApprovalResponse>

    @POST
    suspend fun postCallDetails(
        @Url url: String,
        @Body body: CallDetailsRequest
    ): Response<JsonObject>
}
