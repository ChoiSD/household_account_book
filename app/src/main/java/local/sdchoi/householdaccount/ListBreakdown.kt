package local.sdchoi.householdaccount

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.GestureDetector
import android.view.MotionEvent
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
import local.sdchoi.householdaccount.R.id.*
import local.sdchoi.householdaccount.tool.OnSwipeTouchListener
import java.io.Serializable
import java.lang.reflect.Type
import java.time.LocalDate
import java.util.*
import kotlin.collections.ArrayList

class ListBreakdown: AppCompatActivity() {
    lateinit var mCredential: GoogleAccountCredential
    lateinit var SPREADSHEET_ID: String

    lateinit var month: String

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
        SPREADSHEET_ID = intent.getSerializableExtra(MainActivity.INTENT_SPREADSHEET_ID) as String

        month = LocalDate.now().monthValue.toString()

        GetItem().execute()

        itemTable.setOnTouchListener(SwipeListener())
    }

    private inner class SwipeListener(): View.OnTouchListener {
        private val gestureDetector = GestureDetector(this@ListBreakdown, GestureListener())

        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            return gestureDetector.onTouchEvent(event)
        }

        private inner class GestureListener: GestureDetector.SimpleOnGestureListener() {
            val SWIPE_THRESHOLD = 100
            val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onDown(e: MotionEvent?): Boolean {
                super.onDown(e)
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                super.onFling(e1, e2, velocityX, velocityY)
                var result = false

                try {
                    val diffY = e2!!.y - e1!!.y
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX >  0) {
                                onSwipeRight()
                            } else {
                                onSwipeLeft()
                            }
                            result = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return result
            }
        }

        fun onSwipeRight() {
            if (month != "1") {
                month = (month.toInt() - 1).toString()
            }
            progressBar.visibility = View.VISIBLE
            this@ListBreakdown.overridePendingTransition(R.anim.r2l, R.anim.l2r)
            GetItem().execute()
        }

        fun onSwipeLeft() {
            if (month != "12") {
                month = (month.toInt() + 1).toString()
            }
            progressBar.visibility = View.VISIBLE
            this@ListBreakdown.overridePendingTransition(R.anim.l2r, R.anim.r2l)
            GetItem().execute()
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
                    GetItem().execute()
                }
            }
            MainActivity.REQUEST_AUTHORIZATION_GET -> {
                if (resultCode == Activity.RESULT_OK) {
                    GetItem().execute()
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

    private inner class GetItem: AsyncTask<Void, Void, List<List<Any>>>() {
        var mService: Sheets? = null
        var mLastErr: Exception? = null

        override fun onPreExecute() {
            super.onPreExecute()
            progressBar.visibility = View.VISIBLE

            itemTable.removeAllViews()

            val headerRow = TableRow(this@ListBreakdown)
            headerRow.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

            headerRow.addView(getHeaderView(R.string.table_date))
            headerRow.addView(getHeaderView(R.string.table_type))
            headerRow.addView(getHeaderView(R.string.table_form))
            headerRow.addView(getHeaderView(R.string.table_desc))
            headerRow.addView(getHeaderView(R.string.table_amount))

            itemTable.addView(headerRow)
        }

        private fun getHeaderView(resId: Int): View {
            val view = TextView(this@ListBreakdown)
            view.background = getDrawable(R.drawable.table_header)
            view.textAlignment = View.TEXT_ALIGNMENT_CENTER
            view.textSize = 16.5f
            view.setPadding(3,3,3,3)
            view.setTypeface(null, Typeface.BOLD)
            view.setText(resId)
            return view
        }

        override fun doInBackground(vararg params: Void?): List<List<Any>> {
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mService = Sheets.Builder(transport, jsonFactory, mCredential)
                    .setApplicationName(MainActivity.APPLICATION_NAME)
                    .build()

            itemTitle.text = month + "월"

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
            var range = month + "월!a3:j"

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
                var i = 0
                for (row in result) {
                    appendRow(row, i)
                    i += 1
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

        private fun appendRow(row: List<Any>, index: Int) {
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

            itemRow.setOnClickListener {
                var data = row
                data += (index + 3).toString()
                var putTxIntent = Intent(this@ListBreakdown, PutTransaction::class.java)
                putTxIntent.putExtra(MainActivity.INTENT_SPREADSHEET_ID, SPREADSHEET_ID)
                putTxIntent.putExtra("value", data as Serializable)
                startActivity(putTxIntent)
            }

            itemTable.addView(itemRow)
        }
    }

    companion object {
        val SPREADSHEET_SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS)
    }
}