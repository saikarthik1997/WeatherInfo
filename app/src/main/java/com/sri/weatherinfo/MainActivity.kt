package com.sri.weatherinfo
import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sri.weatherinfo.databinding.ActivityMainBinding
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

class MainActivity : AppCompatActivity() {
    var binding: ActivityMainBinding? = null
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    var alertDialog:AlertDialog?=null
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                requestLocationData()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied for location",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setContentView(binding?.root)
        checkPermissionAndRequestLocationData()


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                getWeatherDetails()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkIfLocationPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }


    override fun onResume() {
        super.onResume()
        Log.d("MainActivity","on resume called")
        if(alertDialog!=null){
            Log.d("MainActivity","alert dialog dismissed")
            alertDialog!!.dismiss()
        }
        if(latitude==0.0 && longitude==0.0)
            checkPermissionAndRequestLocationData()
    }

    private fun checkPermissionAndRequestLocationData() {
        if (!isLocationEnabled()) {
            alertDialog= AlertDialog.Builder(this)
                .setMessage("Your location provider is turned off. Please turn it on for this app to work.")
                .setPositiveButton("Turn On") { d, _ ->
                  //  d.cancel()
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    alertDialog!!.dismiss()
                    startActivity(intent)
                }.show()

        } else {
            if (!checkIfLocationPermissionsGranted()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                ) {
                    showRationalDialogForPermissions()
                } else {
                    // You can directly ask for the permission.
                    // The registered ActivityResultCallback gets the result of this request.
                    requestPermissionLauncher.launch(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )

                }
            } else {
                requestLocationData()
            }


        }

    }


    @SuppressLint("MissingPermission")
    fun requestLocationData() {
        Log.d("MainActivity", "inside requestLocationData")

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, null
        ).addOnSuccessListener { location: Location? ->
            if (location == null) {
                Toast.makeText(this, "Cannot get location.", Toast.LENGTH_SHORT).show()
                binding?.tvWeather?.text = "Failed to get Location ! Please try again"
                binding?.tvWeather?.visibility = View.VISIBLE
                binding?.pbLoading?.visibility = View.INVISIBLE
            }
            else {
                Log.d("MainActivity", "inside success listener")
                latitude = location.latitude
                Log.d("Current Latitude", "$latitude")
                longitude = location.longitude
                Log.d("Current Longitude", "$longitude")
                getWeatherDetails()
            }

        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    private fun getWeatherDetails() {
        binding?.tvWeather?.visibility = View.INVISIBLE
        binding?.pbLoading?.visibility = View.VISIBLE
        val retrofit: Retrofit = Retrofit.Builder()
            // API base URL.
            .baseUrl("http://api.openweathermap.org/data/")
            .build()
        val repo: WeatherRepository =
            retrofit.create(WeatherRepository::class.java)
        val listCall: Call<ResponseBody> = repo.getWeather(
            latitude, longitude, "metric", "316a7277a84229be3fcf54c1978e2037"
        )
        // Callback methods are executed using the Retrofit callback executor.
        listCall.enqueue(object : Callback<ResponseBody> {

            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                if (response.isSuccessful) {
                 //   val weatherResponse: ResponseBody? = response
                    val responseString:String=response.body()!!.string()
                    Log.d("the response is",responseString)
                    binding?.tvWeather?.text = responseString
                    binding?.tvWeather?.visibility = View.VISIBLE
                    binding?.pbLoading?.visibility = View.INVISIBLE

                } else {
                    binding?.tvWeather?.text = "Failed ! Please try again"
                    binding?.tvWeather?.visibility = View.VISIBLE
                    binding?.pbLoading?.visibility = View.INVISIBLE
                    Toast.makeText(
                        this@MainActivity,
                        "failed the response code is ${response.code()}",
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                binding?.tvWeather?.text = "Failed ! Please try again"
                binding?.tvWeather?.visibility = View.VISIBLE
                binding?.pbLoading?.visibility = View.INVISIBLE
                Toast.makeText(
                    this@MainActivity,
                    "failed",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
        })


    }
}