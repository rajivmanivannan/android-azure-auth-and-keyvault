package com.rm.azure.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.volley.Response
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
import com.rm.azure.R.raw
import com.rm.azure.R.string
import com.rm.azure.databinding.FragmentLoginBinding
import com.rm.azure.model.SecretFailureResponseModel
import com.rm.azure.model.SecretSuccessResponseModel
import com.rm.azure.networking.AppEndpoints
import com.rm.azure.networking.MSGraphRequestWrapper
import com.rm.azure.networking.MSGraphRequestWrapper.callGraphAPIUsingVolley
import com.rm.azure.networking.ServiceBuilder
import okhttp3.ResponseBody
import org.json.JSONObject
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


    initializeUI()

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
          exception.message?.let { displayError(it) }
        }
      })
    return binding.root
  }

  /**
   * Initializes UI and callbacks.
   */
  private fun initializeUI() {
    val defaultGraphResourceUrl = MSGraphRequestWrapper.MS_GRAPH_ROOT_ENDPOINT
    binding.msgraphUrl.setText(defaultGraphResourceUrl)
    binding.btnSignIn.setOnClickListener(View.OnClickListener {
      if (mSingleAccountApp == null) {
        return@OnClickListener
      }
      mSingleAccountApp!!.signIn(requireActivity(), null, scopes, authInteractiveCallback)
    })
    binding.btnRemoveAccount.setOnClickListener(View.OnClickListener {
      if (mSingleAccountApp == null) {
        return@OnClickListener
      }
      /**
       * Removes the signed-in account and cached tokens from this app (or device, if the device is in shared mode).
       */
      /**
       * Removes the signed-in account and cached tokens from this app (or device, if the device is in shared mode).
       */
      mSingleAccountApp!!.signOut(object : SignOutCallback {
        override fun onSignOut() {
          mAccount = null
          updateUI()
          showToastOnSignOut()
        }

        override fun onError(exception: MsalException) {
          exception.message?.let { it1 -> displayError(it1) }
        }
      })
    })
    binding.btnCallGraphInteractively.setOnClickListener(View.OnClickListener {
      if (mSingleAccountApp == null) {
        return@OnClickListener
      }
      /**
       * If acquireTokenSilent() returns an error that requires an interaction (MsalUiRequiredException),
       * invoke acquireToken() to have the user resolve the interrupt interactively.
       *
       * Some example scenarios are
       * - password change
       * - the resource you're acquiring a token for has a stricter set of requirement than your Single Sign-On refresh token.
       * - you're introducing a new scope which the user has never consented for.
       */
      /**
       * If acquireTokenSilent() returns an error that requires an interaction (MsalUiRequiredException),
       * invoke acquireToken() to have the user resolve the interrupt interactively.
       *
       * Some example scenarios are
       * - password change
       * - the resource you're acquiring a token for has a stricter set of requirement than your Single Sign-On refresh token.
       * - you're introducing a new scope which the user has never consented for.
       */
      mSingleAccountApp!!.acquireToken(requireActivity(), scopes, authInteractiveCallback)
    })
    binding.btnCallGraphSilently.setOnClickListener(View.OnClickListener {
      if (mSingleAccountApp == null) {
        return@OnClickListener
      }
      /**
       * Once you've signed the user in,
       * you can perform acquireTokenSilent to obtain resources without interrupting the user.
       */
      /**
       * Once you've signed the user in,
       * you can perform acquireTokenSilent to obtain resources without interrupting the user.
       */
      mSingleAccountApp!!.acquireTokenSilentAsync(scopes, mAccount!!.authority, authSilentCallback)
    })
    binding.btnCallUsingAzureFunction.setOnClickListener {

      if (accessToken.isNullOrEmpty()) {
        Toast.makeText(context, getString(string.login_access_token_not_found), Toast.LENGTH_SHORT)
          .show()
      } else {
        binding.txtLog.text = ""
        callKeyVaultAzureFunctionAPI(accessToken!!)
      }

    }
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
          showToastOnSignOut()
        }
      }

      override fun onError(exception: MsalException) {
        displayError(
          exception.printStackTrace()
            .toString()
        )
      }
    })
  }/* Tokens expired or no session, retry with interactive *//* Exception when communicating with the STS, likely config issue *//* Exception inside MSAL, more info inside MsalError.java *//* Failed to acquireToken *//* Successfully got a token, use it to call a protected resource - MSGraph */

  /**
   * Callback used in for silent acquireToken calls.
   */
  private val authSilentCallback: SilentAuthenticationCallback
    get() = object : SilentAuthenticationCallback {
      override fun onSuccess(authenticationResult: IAuthenticationResult) {
        Log.d(TAG, "Successfully authenticated")

        /* Successfully got a token, use it to call a protected resource - MSGraph */callGraphAPI(
          authenticationResult
        )
      }

      override fun onError(exception: MsalException) {
        /* Failed to acquireToken */
        Log.d(TAG, "Authentication failed: $exception")
        exception.message?.let { displayError(it) }
        when (exception) {
          is MsalClientException -> {
            /* Exception inside MSAL, more info inside MsalError.java */
          }
          is MsalServiceException -> {
            /* Exception when communicating with the STS, likely config issue */
          }
          is MsalUiRequiredException -> {
            /* Tokens expired or no session, retry with interactive */
          }
        }
      }
    }/* User canceled the authentication *//* Exception when communicating with the STS, likely config issue *//* Exception inside MSAL, more info inside MsalError.java *//* Failed to acquireToken *//* Successfully got a token, use it to call a protected resource - MSGraph */

  /* Update account */

  /* call graph */
  /**
   * Callback used for interactive request.
   * If succeeds we use the access token to call the Microsoft Graph.
   * Does not check cache.
   */
  private val authInteractiveCallback: AuthenticationCallback
    get() = object : AuthenticationCallback {
      override fun onSuccess(authenticationResult: IAuthenticationResult) {
        /* Successfully got a token, use it to call a protected resource - MSGraph */
        Log.d(TAG, "Successfully authenticated")
        Log.d(TAG, "ID Token: " + authenticationResult.account.idToken)
        accessToken = authenticationResult.account.idToken
        /* Update account */mAccount = authenticationResult.account
        updateUI()

        /* call graph */callGraphAPI(authenticationResult)
      }

      override fun onError(exception: MsalException) {
        /* Failed to acquireToken */
        Log.d(TAG, "Authentication failed: $exception")
        exception.message?.let { displayError(it) }
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

  /**
   * Make an HTTP request to obtain MSGraph data
   */
  private fun callGraphAPI(authenticationResult: IAuthenticationResult) {
    callGraphAPIUsingVolley(
      requireContext(),
      binding.msgraphUrl.text.toString(),
      authenticationResult.accessToken,
      Response.Listener { response -> /* Successfully called graph, process data and send to UI */
        Log.d(TAG, "Response: $response")
        if (response != null) {
          displayGraphResult(response)
        }
      },
      Response.ErrorListener { error ->
        Log.d(TAG, "Error: $error")
        displayError(
          error.printStackTrace()
            .toString()
        )
      })
  }
  //
  // Helper methods manage UI updates
  // ================================
  // displayGraphResult() - Display the graph response
  // displayError() - Display the graph response
  // updateSignedInUI() - Updates UI when the user is signed in
  // updateSignedOutUI() - Updates UI when app sign out succeeds
  //

  /**
   * Make an HTTP request to  obtain secret from KeyVault
   */
  private fun callKeyVaultAzureFunctionAPI(accessToken: String) {
    val request = ServiceBuilder.buildService(AppEndpoints::class.java)
    request.getSecretFromMiddleware("Bearer $accessToken", "MaxRegAuth")
      .enqueue(object : Callback<ResponseBody> {
        override fun onFailure(
          call: Call<ResponseBody>,
          t: Throwable
        ) {
          //handle error here
          Log.d(TAG, "Error: ${t.printStackTrace()}")
          displayError(
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
          binding.txtLog.text = result
        }
      })
  }

  /**
   * Display the graph response
   */
  private fun displayGraphResult(graphResponse: JSONObject) {
    binding.txtLog.text = graphResponse.toString()
  }

  /**
   * Display the error message
   */
  private fun displayError(error: String) {
    binding.txtLog.text = error
  }

  /**
   * Updates UI based on the current account.
   */
  private fun updateUI() {
    if (mAccount != null) {
      binding.btnSignIn.isEnabled = false
      binding.btnRemoveAccount.isEnabled = true
      binding.btnCallGraphInteractively.isEnabled = true
      binding.btnCallGraphSilently.isEnabled = true
      binding.currentUser.text = mAccount!!.username
      binding.btnCallUsingAzureFunction.isEnabled = true

    } else {
      binding.btnSignIn.isEnabled = true
      binding.btnRemoveAccount.isEnabled = false
      binding.btnCallGraphInteractively.isEnabled = false
      binding.btnCallGraphSilently.isEnabled = false
      binding.btnCallUsingAzureFunction.isEnabled = false
      binding.currentUser.text = "None"
    }
    binding.deviceMode.text = if (mSingleAccountApp!!.isSharedDevice) "Shared" else "Non-shared"
  }

  /**
   * Updates UI when app sign out succeeds
   */
  private fun showToastOnSignOut() {
    val signOutText = "Signed Out."
    binding.currentUser.text = ""
    Toast.makeText(context, signOutText, Toast.LENGTH_SHORT)
      .show()
  }

  companion object {
    private val TAG = LoginFragment::class.java.simpleName
  }
}