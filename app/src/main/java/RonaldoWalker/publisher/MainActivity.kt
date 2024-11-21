package RonaldoWalker.publisher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var client: Mqtt5BlockingClient? = null
    private lateinit var locationManager: LocationManager

    private var lastLocation: Location? = null
    private var lastUpdateTime: Long = 0

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {

            val speed = calculateSpeed(location)
            sendLocationToBroker(location.latitude, location.longitude, speed)
            lastLocation = location

        }


        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the MQTT client
        client = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816036438.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        // Initialize Location Manager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Check for location permissions
        checkLocationPermissions()
    }

    // Retrieve Student ID from EditText
    private fun getStudentId(): String {
        val studentIdInput = findViewById<EditText>(R.id.student_ID)
        return studentIdInput.text.toString().trim()
    }

    // Method to start receiving location updates
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permissions are required", Toast.LENGTH_SHORT).show()
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000, // Update every 5 seconds
            2f, // Minimum distance of 5 meters for updates
            locationListener
        )
        Toast.makeText(this, "Location updates started", Toast.LENGTH_SHORT).show()
    }

    // Method to stop receiving location updates
    private fun stopLocationUpdates() {
        locationManager.removeUpdates(locationListener)
        Toast.makeText(this, "Location updates stopped", Toast.LENGTH_SHORT).show()
    }

    // Send location and speed to MQTT broker
    private fun sendLocationToBroker(latitude: Double, longitude: Double, speed: Float) {
        val studentId = getStudentId()
        val message = "StudentID: $studentId, Latitude: $latitude, Longitude: $longitude, Speed: $speed"

        try {
            client?.publishWith()?.topic("assignment/location")?.payload(message.toByteArray())?.send()
            Log.e("MQTT", "Location and speed sent: $message")
            Toast.makeText(this, "Sending data to broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error sending location and speed to broker", Toast.LENGTH_SHORT).show()
        }
    }

    // Start Publishing - Starts location updates and connects to the MQTT broker
    fun startPublishing(view: android.view.View) {
        try {
            client?.connect()
            Toast.makeText(this, "Connected to broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error connecting to broker", Toast.LENGTH_SHORT).show()
        }
        startLocationUpdates()
    }

    // Stop Publishing - Stops location updates and disconnects from the MQTT broker
    fun stopPublishing(view: android.view.View) {
        stopLocationUpdates()
        try {
            client?.disconnect()
            Toast.makeText(this, "Disconnected from broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error disconnecting from broker", Toast.LENGTH_SHORT).show()
        }
    }

    // Request location permissions
    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
        }
    }

    private fun calculateSpeed(location: Location): Float {
        if (lastLocation == null) {
            // If there's no previous location, speed cannot be calculated
            return 0f
        }

        val distance = location.distanceTo(lastLocation!!) // Distance in meters
        val timeElapsed = (location.time - lastLocation!!.time) / 1000.0 // Time in seconds

        return if (timeElapsed > 0) {
            distance.toFloat() / timeElapsed.toFloat() * 3.6f // Speed in km/h
        } else {
            0f
        }
    }

}
