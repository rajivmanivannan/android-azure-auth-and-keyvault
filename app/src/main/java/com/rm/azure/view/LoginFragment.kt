package com.rm.azure.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication.ISingleAccountApplicationCreatedListener
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication.CurrentAccountCallback
import com.microsoft.identity.client.ISingleAccountPublicClientApplication.SignOutCallback
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import com.rm.azure.AppConstants.RESOURCE_URL
import com.rm.azure.AppConstants.SCOPE
import com.rm.azure.AppConstants.SECRET_KEY
import com.rm.azure.R.raw
import com.rm.azure.R.string
import com.rm.azure.databinding.FragmentLoginBinding
import com.rm.azure.model.SecretFailureResponseModel
import com.rm.azure.model.SecretSuccessResponseModel
import com.rm.azure.networking.AppEndpoints
import com.rm.azure.networking.ServiceBuilder
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import java.lang.reflect.Type
import java.util.Locale

/**
 * Implementation sample for 'Single account' mode.
 *
 *
 * If your app only supports one account being signed-in at a time, this is for you.
 * This requires "account_mode" to be set as "SINGLE" in the configuration file.
 * (Please see res/raw/auth_config_single_account.json for more info).
 *
 *
 * Please note that switching mode (between 'single' and 'multiple' might cause a loss of data.
 */
class LoginFragment : Fragment() {
  private lateinit var binding: FragmentLoginBinding
  private var accessToken: String? = null

  /* Azure AD Variables */
  private var mSingleAccountApp: ISingleAccountPublicClientApplication? = null
  private var mAccount: IAccount? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    // Inflate the layout for this fragment
    binding = FragmentLoginBinding.inflate(inflater, container, false)

    binding.scope.setText(SCOPE)
    /**
     * Initializes UI and callbacks.
     */
    binding.resourceUrl.setText(RESOURCE_URL)
    binding.btnSignIn.setOnClickListener(View.OnClickListener {
      if (mSingleAccountApp == null) {
        return@OnClickListener
      }
      mSingleAccountApp!!.signIn(requireActivity(), null, scopes, authInteractiveCallback)
    })
    binding.btnRemoveAccount.setOnClickListener {
      signOut()
    }
    binding.btnCallUsingAzureFunction.setOnClickListener {

      if (accessToken.isNullOrEmpty()) {
        signOut()
        Toast.makeText(context, getString(string.login_access_token_not_found), Toast.LENGTH_SHORT)
          .show()
      } else {
        binding.txtLog.text = ""
        callKeyVaultMiddlewareApi(accessToken!!)
      }
    }

    // Creates a PublicClientApplication object with res/raw/auth_config_single_account.json
    PublicClientApplication.createSingleAccountPublicClientApplication(
      requireContext(),
      raw.auth_config_single_account,
      object : ISingleAccountApplicationCreatedListener {
        override fun onCreated(application: ISingleAccountPublicClientApplication) {
          /**
           * This test app assumes that the app is only going to support one account.
           * This requires "account_mode" : "SINGLE" in the config json file.
           */
          mSingleAccountApp = application
          loadAccount()
        }

        override fun onError(exception: MsalException) {
          exception.message?.let { displayMessage(it) }
        }
      })

    return binding.root
  }

  override fun onResume() {
    super.onResume()
    /**
     * The account may have been removed from the device (if broker is in use).
     *
     * In shared device mode, the account might be signed in/out by other apps while this app is not in focus.
     * Therefore, we want to update the account state by invoking loadAccount() here.
     */
    loadAccount()
  }

  /**
   * Extracts a scope array from a text field,
   * i.e. from "User.Read User.ReadWrite" to ["user.read", "user.readwrite"]
   */
  private val scopes: Array<String>
    get() = binding.scope.text.toString()
      .lowercase(Locale.getDefault())
      .split(" ")
      .toTypedArray()

  /**
   * Load the currently signed-in account, if there's any.
   */
  private fun loadAccount() {
    if (mSingleAccountApp == null) {
      return
    }
    mSingleAccountApp!!.getCurrentAccountAsync(object : CurrentAccountCallback {
      override fun onAccountLoaded(activeAccount: IAccount?) {
        // You can use the account data to update your UI or your app database.
        mAccount = activeAccount
        updateUI()
      }

      override fun onAccountChanged(
        priorAccount: IAccount?,
        currentAccount: IAccount?
      ) {
        if (currentAccount == null) {
          // Perform a cleanup task as the signed-in account changed.
          binding.currentUser.text = ""
          binding.txtLog.text = ""
        }
      }

      override fun onError(exception: MsalException) {
        displayMessage(
          exception.printStackTrace()
            .toString()
        )
      }
    })
  }
  /* Update account */

  /**
   * Callback used for interactive request.
   * If succeeds we use the access token to call the Protected Resources.
   * Does not check cache.
   */
  private val authInteractiveCallback: AuthenticationCallback
    get() = object : AuthenticationCallback {
      override fun onSuccess(authenticationResult: IAuthenticationResult) {
        /* Successfully got a token, use it to call a protected resource - MSGraph */
        Log.d(TAG, "Successfully authenticated")
        Log.d(TAG, "AccessToken: " + authenticationResult.accessToken)
        accessToken = authenticationResult.accessToken
        /* Update account */mAccount = authenticationResult.account
        updateUI()
      }

      override fun onError(exception: MsalException) {
        /* Failed to acquireToken */
        Log.d(TAG, "Authentication failed: $exception")
        exception.message?.let { displayMessage(it) }
        if (exception is MsalClientException) {
          /* Exception inside MSAL, more info inside MsalError.java */
        } else if (exception is MsalServiceException) {
          /* Exception when communicating with the STS, likely config issue */
        }
      }

      override fun onCancel() {
        /* User canceled the authentication */
        Log.d(TAG, "User cancelled login.")
      }
    }

  //
  // Helper methods manage UI updates
  // ================================
  // displayMessage() - Display the response
  // updateSignedInUI() - Updates UI when the user is signed in
  // updateSignedOutUI() - Updates UI when app sign out succeeds
  //

  /**
   * Make an HTTP request to KeyVaultMiddlewareApi to obtain secret from KeyVault
   */
  private fun callKeyVaultMiddlewareApi(accessToken: String) {
    val request = ServiceBuilder.buildService(AppEndpoints::class.java)
    request.getSecretFromMiddleware("Bearer $accessToken", SECRET_KEY)
      .enqueue(object : Callback<ResponseBody> {
        override fun onFailure(
          call: Call<ResponseBody>,
          t: Throwable
        ) {
          //handle error here
          Log.d(TAG, "Error: ${t.printStackTrace()}")
          displayMessage(
            t.printStackTrace()
              .toString()
          )
        }

        override fun onResponse(
          call: Call<ResponseBody>,
          response: retrofit2.Response<ResponseBody>
        ) {
          val gson = Gson()
          val result = if (response.isSuccessful) {
            val type: Type = object : TypeToken<SecretSuccessResponseModel?>() {}.type
            val secretSuccessResponseModel: SecretSuccessResponseModel =
              gson.fromJson(
                response.body()!!
                  .charStream(), type
              )
            "${secretSuccessResponseModel.key} : ${secretSuccessResponseModel.secret}"
          } else {
            val type: Type = object : TypeToken<SecretFailureResponseModel?>() {}.type
            val secretFailureResponseModel: SecretFailureResponseModel =
              gson.fromJson(
                response.errorBody()!!
                  .charStream(), type
              )
            "${secretFailureResponseModel.error} : ${secretFailureResponseModel.message}"
          }
          displayMessage(result)
        }
      })
  }

  /**
   * Display the  message
   */
  private fun displayMessage(message: String) {
    binding.txtLog.text = message
  }

  /**
   * Updates UI based on the current account.
   */
  private fun updateUI() {
    if (mAccount != null) {
      binding.btnSignIn.isEnabled = false
      binding.btnRemoveAccount.isEnabled = true
      binding.currentUser.text = mAccount!!.username
      binding.btnCallUsingAzureFunction.isEnabled = true

    } else {
      binding.btnSignIn.isEnabled = true
      binding.btnRemoveAccount.isEnabled = false
      binding.btnCallUsingAzureFunction.isEnabled = false
      binding.currentUser.text = getString(string.login_user_none)
    }
    binding.deviceMode.text = if (mSingleAccountApp!!.isSharedDevice) getString(
      string.login_device_mode_shared
    ) else getString(
      string.login_device_mode_non_shared
    )
  }

  private fun signOut() {
    if (mSingleAccountApp == null) {
      return
    }
    /**
     * Removes the signed-in account and cached tokens from this app (or device, if the device is in shared mode).
     */
    mSingleAccountApp!!.signOut(object : SignOutCallback {
      override fun onSignOut() {
        mAccount = null
        updateUI()
        binding.currentUser.text = ""
        binding.txtLog.text = ""
        Toast.makeText(context, getString(string.login_signed_out), Toast.LENGTH_SHORT)
          .show()
      }

      override fun onError(exception: MsalException) {
        exception.message?.let { it -> displayMessage(it) }
      }
    })
  }

  companion object {
    private val TAG = LoginFragment::class.java.simpleName
  }
}