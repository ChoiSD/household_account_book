package local.sdchoi.householdaccount

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.TableRow
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
import kotlinx.android.synthetic.main.activity_list_breakdown.*
import java.time.LocalDate
import java.util.*
import kotlin.collections.ArrayList

class ListBreakdown: AppCompatActivity() {
    lateinit var mCredential: GoogleAccountCredential
    lateinit var SPREADSHEET_ID: String

    val month = LocalDate.now().monthValue.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_breakdown)

        // Initialize Google Account Credential
        mCredential = GoogleAccountCredential.usingOAuth2(applicationContext, SPREADSHEET_SCOPES)
                .setBackOff(ExponentialBackOff())
                .setSelectedAccountName(
                        getSharedPreferences(MainActivity.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                        .getString(MainActivity.PREF_ACCOUNT_NAME, null)
                )

        // Get Spreadsheet ID
        SPREADSHEET_ID = intent.getSerializableExtra(MainActivity.PREF_SPREADSHEET_ID) as String

        itemTitle.text = month + "월"

        progressBar.visibility = View.VISIBLE

        GetItem().execute(month)
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
                    GetItem().execute(month)
                }
            }
            MainActivity.REQUEST_AUTHORIZATION_GET -> {
                if (resultCode == Activity.RESULT_OK) {
                    GetItem().execute(month)
                }
            }
        }
    }

    private fun showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode: Int) {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val dialog = apiAvailability.getErrorDialog(this@ListBreakdown,
                connectionStatusCode,
                MainActivity.REQUEST_GOOGLE_PLAY_SERVICES)
        dialog.show()
    }

    private inner class GetItem: AsyncTask<String, Void, List<List<Any>>>() {
        var mService: Sheets? = null
        var mLastErr: Exception? = null

        override fun doInBackground(vararg month: String?): List<List<Any>> {
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

        private fun getData(): List<List<Any>> {
            val result = ArrayList<List<Any>>()
            var range = itemTitle.text.toString() + "!a3:j"

            val response = mService!!.spreadsheets()
                    .values().get(SPREADSHEET_ID, range)
                    .execute()
            val value = response.getValues()
            if (value != null) {
                for (row in value) {
                    if (row[0].toString().length != 0) {
                        result.add(row)
                    }
                }
            }

            return result
        }

        override fun onPostExecute(result: List<List<Any>>?) {
            super.onPostExecute(result)

            if (result != null) {
                for (row in result) {
                    appendRow(row)
                }
            }

            progressBar.visibility = View.GONE
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

        private fun appendRow(row: List<Any>) {
            val itemRow = TableRow(this@ListBreakdown)
            itemRow.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

            val dateView = TextView(this@ListBreakdown)
            dateView.background = getDrawable(R.drawable.table_row)
            dateView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            dateView.textSize = 18F
            dateView.setPadding(3,3,3,3)
            dateView.text = row[0].toString()
            itemRow.addView(dateView)

            val typeView = TextView(this@ListBreakdown)
            typeView.background = getDrawable(R.drawable.table_row)
            typeView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            typeView.textSize = 18F
            typeView.setPadding(3,3,3,3)
            typeView.text = row[2].toString()
            itemRow.addView(typeView)

            val formView = TextView(this@ListBreakdown)
            formView.background = getDrawable(R.drawable.table_row)
            formView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            formView.textSize = 18F
            formView.setPadding(3,3,3,3)
            formView.text = row[3].toString()
            itemRow.addView(formView)

            val descView = TextView(this@ListBreakdown)
            descView.background = getDrawable(R.drawable.table_row)
            descView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            descView.textSize = 18F
            descView.setPadding(3,3,3,3)
            descView.text = row[1].toString()
            itemRow.addView(descView)

            val amountView = TextView(this@ListBreakdown)
            amountView.background = getDrawable(R.drawable.table_row)
            amountView.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            amountView.textSize = 18F
            amountView.setPadding(3,3,3,3)
            if (row[2].toString() == "수입") {
                amountView.text = row[4].toString()
            } else {
                amountView.text = row[5].toString()
            }
            itemRow.addView(amountView)

            itemTable.addView(itemRow)
        }
    }

    companion object {
        val SPREADSHEET_SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS)
    }
}