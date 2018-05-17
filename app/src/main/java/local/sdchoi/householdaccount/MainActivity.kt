package local.sdchoi.householdaccount

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.android.synthetic.main.activity_main.*
import local.sdchoi.householdaccount.tool.ObjectSerializer
import pub.devrel.easypermissions.EasyPermissions
import java.io.Serializable
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var mCredentials: GoogleAccountCredential
    var itemsMap = mapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mCredentials = GoogleAccountCredential.usingOAuth2(applicationContext, SPREADSHEET_SCOPES)
                .setBackOff(ExponentialBackOff())

        fab.setOnClickListener {
            // show alertdialog to add spreadsheet docs
            val dialog = AlertDialog.Builder(this)
            val inflater = this.layoutInflater.inflate(R.layout.dialog_layout, null)
            dialog.setTitle("Add Spreadsheet")
                    .setView(inflater)
                    .setPositiveButton(R.string.docs_add, DialogInterface.OnClickListener(
                            fun (_: DialogInterface, _: Int) {
                                addItemOnItemLayout(inflater)
                            }
                    ))
                    .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener(
                            fun (_: DialogInterface, _: Int) {
                                Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                            }
                    ))
                    .create()
            dialog.show()
        }

        showSavedItems()

        getPermissionAndChooseAccount()
    }

    /**
     * Show saved items on activity_main
     */
    private fun showSavedItems() {
        itemsMap = ObjectSerializer.deserialize(getPreferences(Context.MODE_PRIVATE)
                .getString(PREF_SAVED_ITEMS, ObjectSerializer.serialize(emptyMap<String,String>() as Serializable)))
                 as Map<String, String>
        if (itemsMap.isNotEmpty()) {
            for (key in itemsMap.keys) {
                val childView = TextView(this)
                childView.text = key
                childView.setTextSize(TypedValue.COMPLEX_UNIT_SP,25f)
                childView.setOnClickListener {
                    switchToPutTransactionActivity(itemsMap.get(key)!!)
                }
                itemLayout.addView(childView)
            }
        }
    }

    /**
     * Add item on activity_main
     * @param dialog View: a view which contains user's input
     *
     */
    private fun addItemOnItemLayout(dialog: View) {
        val name = dialog.findViewById<EditText>(R.id.docsNameText)
                .text.toString()

        if (isValueEmptyInSharedPreference(name)) {
            val inputUrl= dialog.findViewById<EditText>(R.id.docsUriText)
                    .text.toString()
            // Get spreadsheet ID
            val regexGetId = "https:\\/\\/docs.google.com\\/spreadsheets\\/d\\/([\\w-_]+)\\/[\\w\\W]+".toRegex()
            val matchedResult = regexGetId.find(inputUrl)
            if (matchedResult != null) {
                val (spreadsheetId) = matchedResult.destructured
                // Configure textview which will be listed on activity_main.xml
                val childView = TextView(this)
                childView.text = name
                childView.setTextSize(TypedValue.COMPLEX_UNIT_SP,25f)
                childView.setOnClickListener {
                    switchToPutTransactionActivity(spreadsheetId)
                }
                // Save name and spreadsheet ID in preference
                itemsMap = itemsMap.plus(name to spreadsheetId)
                val editor = getPreferences(Context.MODE_PRIVATE).edit()
                editor.putString(PREF_SAVED_ITEMS, ObjectSerializer.serialize(itemsMap as Serializable))
                editor.commit()
                itemLayout.addView(childView)
                Snackbar.make(itemLayout, "item added", Snackbar.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "You put a wrong URL.", Toast.LENGTH_SHORT).show()
                fab.performClick()
            }
        } else {
            Toast.makeText(this, "Same name already exists", Toast.LENGTH_SHORT).show()
            fab.performClick()
        }
    }

    /**
     * Check if a value in shared preference which is mapped with 'key' is empty
     * @param key String : a key indicates value
     * @return Boolean : true if empty, otherwise false
     */
    private fun isValueEmptyInSharedPreference(key: String):Boolean {
        return getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getString(key, null) == null
    }

    private fun getPermissionAndChooseAccount() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices()
        } else if (isValueEmptyInSharedPreference(PREF_ACCOUNT_NAME)) {
            chooseAccount()
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
        chooseAccount()
    }

    /**
     * Check if Google Play service is available
     * @return true if Google Play service is available and up to date, false otherwise
     */
    private fun isGooglePlayServicesAvailable(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this)
        return connectionStatusCode == ConnectionResult.SUCCESS
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private fun acquireGooglePlayServices() {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this)
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode)
        }
    }

    private fun showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode: Int) {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val dialog = apiAvailability.getErrorDialog(this, connectionStatusCode, REQUEST_GOOGLE_PLAY_SERVICES)
        dialog.show()
    }

    private fun chooseAccount() {
        if (EasyPermissions.hasPermissions(applicationContext, Manifest.permission.GET_ACCOUNTS)) {
            startActivityForResult(mCredentials.newChooseAccountIntent(),
                    REQUEST_ACCOUNT_PICKER)
        } else {
            EasyPermissions.requestPermissions(this,
                    "Need to access your Google account",
                    REQUEST_PERMISSION_GET_ACCOUNT,
                    Manifest.permission.GET_ACCOUNTS)
        }
    }

    private fun switchToPutTransactionActivity(spreadsheetId: String) {
        var putTxIntent = Intent(this, PutTransaction::class.java)
        putTxIntent.putExtra(PREF_SPREADSHEET_ID, spreadsheetId)
        startActivity(putTxIntent)
    }

    private fun quitActivity(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(homeIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_GOOGLE_PLAY_SERVICES -> {
                if (resultCode != Activity.RESULT_OK) {
                    quitActivity("Failed to get Google Play service. Please install Google Play " +
                            "services on your device and relaunch this app.")
                } else {
                    getPermissionAndChooseAccount()
                }
            }
            REQUEST_ACCOUNT_PICKER -> {
                if (resultCode == Activity.RESULT_OK && data != null && data.extras != null) {
                    val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if (accountName != null) {
                        //val editor = getPreferences(Context.MODE_PRIVATE).edit()
                        val editor = getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).edit()
                        editor.putString(PREF_ACCOUNT_NAME, accountName)
                        editor.commit()
                    }
                }
            }
        }
    }

    companion object {
        const val APPLICATION_NAME = "Household Account"
        const val SHARED_PREF_NAME = "household.conf"

        // request codes
        const val REQUEST_GOOGLE_PLAY_SERVICES: Int = 1000
        const val REQUEST_PERMISSION_GET_ACCOUNT: Int = 1001
        const val REQUEST_ACCOUNT_PICKER: Int = 1002
        const val REQUEST_AUTHORIZATION_GET: Int = 1003
        const val REQUEST_AUTHORIZATION_PUT: Int = 1004

        val SPREADSHEET_SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS)
        const val PREF_ACCOUNT_NAME: String = "accountName"
        const val PREF_SPREADSHEET_ID: String = "sheetId"
        const val PREF_SAVED_ITEMS: String = "savedItems"
    }
}
