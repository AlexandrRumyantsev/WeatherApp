package com.example.weatherapp.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.weatherapp.DialogManager
import com.example.weatherapp.MainViewModel
import com.example.weatherapp.adapters.WeatherAdapter
import com.example.weatherapp.adapters.WeatherModel
import com.example.weatherapp.databinding.FragmentMainBinding
import com.example.weatherapp.utils.SpacingItemDecorator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject

const val API_KEY = "b992f60a8da24b96842111948232005"
class MainFragment : Fragment() {
    private lateinit var pLauncher: ActivityResultLauncher<String>
    private lateinit var binding: FragmentMainBinding
    private lateinit var adapter: WeatherAdapter
    private lateinit var fLocationClient: FusedLocationProviderClient
    private val model: MainViewModel by activityViewModels()
    private val hoursModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermission()
        init()
        checkLocation()
        updateCurrentCard()
        updateHoursRc()
    }

    override fun onResume() {
        super.onResume()
        checkLocation()
    }
    private fun permissionListener(){
        pLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()){
            Toast.makeText(activity, "Permission is $it", Toast.LENGTH_SHORT).show()
        }
    }
    private fun checkPermission(){
        if(!isPermissonGranted(Manifest.permission.ACCESS_FINE_LOCATION)){
            permissionListener()
            pLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    private fun init() = with(binding){
        initRcView()
        initFLocationClient()
        ivSearch.setOnClickListener {
            DialogManager.searchByNameDialog(requireContext(), object : DialogManager.Listener {
                override fun onClick(name: String?) {
                    name?.let { it1 -> requestWeatherData(it1) }
                }
            })
        }
        ivSync.setOnClickListener{
            checkLocation()
        }
    }
    private fun initRcView() = with(binding){
        rcView.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        adapter = WeatherAdapter()
        rcView.adapter= adapter
        val itemDecorator = SpacingItemDecorator(20)
        rcView.addItemDecoration(itemDecorator)
    }
    private fun initFLocationClient(){
        fLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
    }
    private fun isLocationEnabled():Boolean{
        val lm = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
    private fun checkLocation(){
        if(isLocationEnabled()){
            getLocation()
        }
        else{
            DialogManager.locationSettingsDialog(requireContext(), object: DialogManager.Listener{
                override fun onClick(name: String?) {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            })
        }
    }
    private fun getLocation(){
        if(!isLocationEnabled()){
            Toast.makeText(requireContext(), "Location disabled!", Toast.LENGTH_SHORT).show()
            return
        }
        val ct = CancellationTokenSource()
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, ct.token)
            .addOnCompleteListener{
                requestWeatherData("${it.result.latitude}, ${it.result.longitude}")
            }
    }
    private fun getHoursList(wItem: WeatherModel) : List<WeatherModel>{
        val hoursArray = JSONArray(wItem.hours)
        val list = ArrayList<WeatherModel>()
        for(i in 0 until hoursArray.length()){
            val item = WeatherModel(
                "",
                (hoursArray[i] as JSONObject).getString("time"),
                (hoursArray[i] as JSONObject).getString("time"),
                (hoursArray[i] as JSONObject).getJSONObject("condition")
                    .getString("text"),
                (hoursArray[i] as JSONObject).getString("temp_c"),
                "",
                "",
                (hoursArray[i] as JSONObject).getJSONObject("condition")
                    .getString("icon"),
                "",
                "",
                ""
            )
            list.add(item)
        }
        return list
    }
    private fun requestWeatherData(city: String){
        val url = "https://api.weatherapi.com/v1/forecast.json?key=" +
                API_KEY +
                "&q=" +
                city +
                "&" +
                "days=" +
                "1" +
                "&aqi=no&alerts=no"
        val queue = Volley.newRequestQueue(context)
        val request = StringRequest(
            Request.Method.GET,
            url,
            {
                result -> parseWeatherData(result)
            },
            {
                error -> Log.d("MyLog","Error: $error")
            }
        )
        queue.add(request)
    }
    private fun parseWeatherData(result: String) {
        val mainObject = JSONObject(result)
        val list = parseDays(mainObject)
        parseCurrentData(mainObject, list[0])
    }
    private fun parseCurrentData(mainObject: JSONObject, weatherItem: WeatherModel){
        val item = WeatherModel(
            mainObject.getJSONObject("location").getString("name"),
            mainObject.getJSONObject("current").getString("last_updated"),
            mainObject.getJSONObject("current").getString("last_updated"), /// TODO: get day name
            mainObject.getJSONObject("current").getJSONObject("condition")
                .getString("text"),
            mainObject.getJSONObject("current").getString("temp_c"),
            weatherItem.minTemp,
            weatherItem.maxTemp,
            mainObject.getJSONObject("current").getJSONObject("condition")
                .getString("icon"),
            mainObject.getJSONObject("current").getString("wind_kph"),
            mainObject.getJSONObject("current").getString("humidity"),
            weatherItem.hours
        )
        model.liveDataCurrent.value = item
    }
    private fun parseDays(mainObject: JSONObject): List<WeatherModel>{
        val list = ArrayList<WeatherModel>()
        val daysArray = mainObject.getJSONObject("forecast")
            .getJSONArray("forecastday")
        val name = mainObject.getJSONObject("location").getString("name")
        for(i in 0 until daysArray.length()){
            val day = daysArray[i] as JSONObject
            val item = WeatherModel(
                name,
                day.getString("date"),
                day.getString("date"),
                day.getJSONObject("day").getJSONObject("condition")
                    .getString("text"),
                "",
                day.getJSONObject("day").getString("mintemp_c"),
                day.getJSONObject("day").getString("maxtemp_c"),
                day.getJSONObject("day").getJSONObject("condition")
                    .getString("icon"),
                "",
                "",
                day.getJSONArray("hour").toString()
            )
            list.add(item)
        }
        return list
    }

    private fun updateHoursRc() = with(binding){
        hoursModel.liveDataCurrent.observe(viewLifecycleOwner){
            adapter.submitList(getHoursList(it))
        }
    }
    @SuppressLint("SetTextI18n")
    private fun updateCurrentCard() = with(binding){
        model.liveDataCurrent.observe(viewLifecycleOwner){
            tvCity.text = it.city
            tvTime.text = it.time.substringAfter(' ')
            Picasso.get().load("https://${it.imageUrl}").into(ivCondition)
            tvTemp.text = "${it.currentTemp.substringBefore('.')}°С"
            tvCondition.text = it.condition
            tvWindSpeed.text = "${it.windSpeed} km/h"
            tvTempPr.text = "${it.minTemp.substringBefore('.')}°С/" +
                    "${it.maxTemp.substringBefore('.')}°С"
            tvWet.text = "${it.wet}%"
        }
    }
    companion object {
        @JvmStatic
        fun newInstance() = MainFragment()
    }
}