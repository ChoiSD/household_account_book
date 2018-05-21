package local.sdchoi.householdaccount

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.android.synthetic.main.activity_put_transaction.*
import local.sdchoi.householdaccount.tool.ObjectSerializer
import java.io.Serializable
import java.time.LocalDate
import java.util.*
import kotlin.collections.ArrayList

class PutTransaction: AppCompatActivity() {
    lateinit var mCredential: GoogleAccountCredential
    lateinit var SPREADSHEET_ID: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_put_transaction)

        setDateHint()
        putButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            putButton.isEnabled = false
            putDataToSheet()
        }

        // Initialize Google Account Credential
        mCredential = GoogleAccountCredential.usingOAuth2(applicationContext, SPREADSHEET_SCOPES)
               .setBackOff(ExponentialBackOff())
        mCredential.selectedAccountName = getSharedPreferences(MainActivity.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                .getString(MainActivity.PREF_ACCOUNT_NAME, null)

        // Get Spreadsheet ID
        SPREADSHEET_ID = intent.getSerializableExtra(MainActivity.PREF_SPREADSHEET_ID) as String

        prepareSpinner()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_put_transaction, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            when (item.itemId) {
                R.id.breakdown -> {
                    // Switch to ListBreakdown activity
                   var listIntent = Intent(this, ListBreakdown::class.java)
                    listIntent.putExtra(MainActivity.PREF_SPREADSHEET_ID, SPREADSHEET_ID)
                    startActivity(listIntent)
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Set date hint as current date to few textViews
     */
    private fun setDateHint() {
        val currentDate = LocalDate.now()
        yearText.hint = currentDate.year.toString()
        monthText.hint = currentDate.monthValue.toString()
        dayText.hint = currentDate.dayOfMonth.toString()
    }

    /**
     * Get data to put on Spreadsheet
     */
    private fun getInputData(): List<String> {
        val result = ArrayList<String>(10)
        if (typeSpinner.selectedItem.toString() == "수입") {
            result.add(getText(yearText) + ". " + getText(monthText) + ". " + getText(dayText))
            result.add(descText.text.toString())
            result.add(typeSpinner.selectedItem.toString())
            result.add(formSpinner.selectedItem.toString())
            result.add(amoutText.text.toString())
            result.add("")
            result.add("")
            result.add("")
            result.add("")
            result.add(detailDescText.text.toString())
        } else {
            result.add(getText(yearText) + ". " + getText(monthText) + ". " + getText(dayText))
            result.add(descText.text.toString())
            result.add(typeSpinner.selectedItem.toString())
            result.add(formSpinner.selectedItem.toString())
            result.add("")
            result.add(amoutText.text.toString())
            result.add("")
            result.add("")
            result.add("")
            result.add(detailDescText.text.toString())
        }
        return result
    }

    /**
     * Get text data from TextView, if not, get hint data
     */
    private fun getText(textView: TextView): String {
        if (textView.text.isEmpty()) {
            return textView.hint.toString()
        } else {
            return textView.text.toString()
        }
    }

    /**
     * Put data to Google spreadsheet
     */
    private fun putDataToSheet() {
        if (!isDeviceOnline()) {
            Toast.makeText(this, "No network connection available", Toast.LENGTH_LONG).show()
        } else {
            PutDataThruApi().execute(getInputData())
        }
    }

    /**
     * Prepare spinner's dropdown menu
     */
    private fun prepareSpinner() {
        val serializedEmptyList = ObjectSerializer.serialize(emptyList<String>() as Serializable)
        val typeData = ObjectSerializer.deserialize(
                getPreferences(Context.MODE_PRIVATE).getString(PREF_TYPE_DATA, serializedEmptyList)
        ) as List<String>
        val formData = ObjectSerializer.deserialize(
                getPreferences(Context.MODE_PRIVATE).getString(PREF_FORM_DATA, serializedEmptyList)
        ) as List<String>

        if (!isDeviceOnline()) {
            Toast.makeText(this, "No network connection available", Toast.LENGTH_LONG).show()
        } else if (typeData.isEmpty() || formData.isEmpty()) {
            GetDataFromApi().execute()
        } else {
            for (i in 0..1) {
                var data: List<String>
                if (i == 0) {
                    data = typeData
                } else {
                    data = formData
                }

                val aa = ArrayAdapter(this,
                        android.R.layout.simple_spinner_item,
                        data)
                aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                if (data[0] == "항목") {
                    typeSpinner.adapter = aa
                } else {
                    formSpinner.adapter = aa
                }
            }
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            MainActivity.REQUEST_AUTHORIZATION_PUT -> {
                if (resultCode == Activity.RESULT_OK) {
                    putDataToSheet()
                }
            }
            MainActivity.REQUEST_AUTHORIZATION_GET -> {
                if (resultCode == Activity.RESULT_OK) {
                    prepareSpinner()
                }
            }
        }
    }


    /**
     * Check if device can connect to Internet
     * @return true if device has network connection, false otherwise
     */
    private fun isDeviceOnline(): Boolean {
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = connMgr.activeNetworkInfo
        return (netInfo != null && netInfo.isConnected)
    }

    private fun showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode: Int) {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val dialog = apiAvailability.getErrorDialog(this@PutTransaction,
                connectionStatusCode,
                MainActivity.REQUEST_GOOGLE_PLAY_SERVICES)
        dialog.show()
    }

    /**
     * Asynchronous thead to put transaction into spreadsheet
     * @params data List<String> : input data to spreadsheet
     */
    private inner class PutDataThruApi: AsyncTask<List<String>, Void, Boolean>() {

        var mService: Sheets? = null
        var mLastErr: Exception? = null

        override fun doInBackground(vararg params: List<String>?): Boolean {
            val data = params[0]
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mService = Sheets.Builder(transport, jsonFactory, mCredential)
                    .setApplicationName(MainActivity.APPLICATION_NAME)
                    .build()

            try {
                return putData(data!!)
            } catch (e: Exception) {
                mLastErr = e
                cancel(true)
                return false
            }
        }

        override fun onPostExecute(result: Boolean?) {
            super.onPostExecute(result)
            if (result!!) {
                putButton.isEnabled = true
                yearText.text = null
                monthText.text = null
                dayText.text = null
                typeSpinner.setSelection(0)
                formSpinner.setSelection(0)
                amoutText.text = null
                descText.text = null
                detailDescText.text = null
                progressBar.visibility = View.GONE
                Snackbar.make(linearLayout, "item added", Snackbar.LENGTH_LONG).show()
            }
        }

        override fun onCancelled() {
            super.onCancelled()
            if (mLastErr != null) {
                if (mLastErr is GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            (mLastErr as GooglePlayServicesAvailabilityIOException).connectionStatusCode)
                } else if (mLastErr is UserRecoverableAuthIOException) {
                    startActivityForResult((mLastErr as UserRecoverableAuthIOException).intent,
                            MainActivity.REQUEST_AUTHORIZATION_PUT)
                } else {
                    Toast.makeText(applicationContext,
                            "The following error occurred:\n" + mLastErr!!.message,
                            Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(applicationContext, "Request cancelled", Toast.LENGTH_LONG).show()
            }
        }

        /**
         * Put data into spreadsheet
         * @params data List<String> : input data
         */
        private fun putData(data: List<String>): Boolean {
            val range = getText(monthText) + "월!a1:j1"
            val valueRange = ValueRange()
                    .setRange(range)
                    .setValues(Arrays.asList(data) as List<MutableList<Any>>?)
            val request = mService!!.spreadsheets().values()
                    .append(SPREADSHEET_ID, range, valueRange)
                    .setInsertDataOption("INSERT_ROWS")
                    .setValueInputOption("USER_ENTERED")
            val response = request.execute()
            return true
        }
    }

    /**
     * Asynchronous thead to get data from spreadsheet
     * @params spinners Spinner : spinner which will receive dropdown menu items
     */
    private inner class GetDataFromApi: AsyncTask<Void, Void, List<List<String>>>() {
        var mService: Sheets? = null
        var mLastErr: Exception? = null

        override fun doInBackground(vararg p: Void): List<List<String>> {
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mService = Sheets.Builder(transport, jsonFactory, mCredential)
                    .setApplicationName(MainActivity.APPLICATION_NAME)
                    .build()
            try {
                return getData()
            } catch (e: Exception) {
                mLastErr = e
                cancel(true)
                return emptyList()
            }
        }

        override fun onPostExecute(results: List<List<String>>?) {
            super.onPostExecute(results)
            if (results != null) {
                val editor = getPreferences(Context.MODE_PRIVATE).edit()
                for (result in results) {
                    val aa = ArrayAdapter(this@PutTransaction,
                            android.R.layout.simple_spinner_item,
                            result)
                    aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    if (result[0] == "항목") {
                        typeSpinner.adapter = aa
                        editor.putString(PREF_TYPE_DATA, ObjectSerializer.serialize(result as Serializable))
                        editor.commit()
                    } else {
                        formSpinner.adapter = aa
                        editor.putString(PREF_FORM_DATA, ObjectSerializer.serialize(result as Serializable))
                        editor.commit()
                    }
                }
            } else {
                Toast.makeText(this@PutTransaction,
                        "Failed to get data from spreadsheet:",
                        Toast.LENGTH_LONG).show()
            }
        }

        override fun onCancelled() {
            super.onCancelled()
            if (mLastErr != null) {
                if (mLastErr is GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            (mLastErr as GooglePlayServicesAvailabilityIOException).connectionStatusCode)
                } else if (mLastErr is UserRecoverableAuthIOException) {
                    startActivityForResult((mLastErr as UserRecoverableAuthIOException).intent,
                            MainActivity.REQUEST_AUTHORIZATION_GET)
                } else {
                    Toast.makeText(applicationContext,
                            "The following error occurred:\n" + mLastErr!!.message,
                            Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(applicationContext, "Request cancelled", Toast.LENGTH_LONG).show()
            }
        }

        private fun getData(): List<List<String>> {
            val results = ArrayList<List<String>>()

            for (i in 0..1) {
                val result = ArrayList<String>()
                var range: String
                if (i == 0) {
                    result.add("항목")
                    range = "개요!b3:b19"
                } else {
                    result.add("형태")
                    range = "개요!b27:b30"
                }
                val response = mService!!.spreadsheets().values()
                        .get(SPREADSHEET_ID, range)
                        .execute()
                val value = response.getValues()
                if (value != null) {
                    for (row in value) {
                        result.add(row[0].toString())
                    }
                }
                results.add(result)
            }

            return results
        }
    }

    companion object {
        val SPREADSHEET_SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS)
        val PREF_TYPE_DATA: String = "typeData"
        val PREF_FORM_DATA: String = "formData"
    }

}