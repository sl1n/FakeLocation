package com.xposed.hook

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellLocation
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.telephony.gsm.GsmCellLocation
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.xposed.hook.config.Constants
import com.xposed.hook.config.PkgConfig
import com.xposed.hook.entity.AppInfo
import com.xposed.hook.entity.LocationHistory
import com.xposed.hook.entity.LocationHistoryManager
import com.xposed.hook.theme.AppTheme
import com.xposed.hook.utils.CellLocationHelper
import com.xposed.hook.utils.SharedPreferencesHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RimetActivity : AppCompatActivity() {

    private lateinit var sp: SharedPreferences
    private lateinit var appInfo: AppInfo
    private var isDingTalk = false

    private lateinit var tm: TelephonyManager
    private lateinit var l: GsmCellLocation
    private lateinit var lm: LocationManager
    private lateinit var gpsL: Location

    private val _currentLatitude = MutableLiveData("")
    private val _currentLongitude = MutableLiveData("")
    private val _currentLac = MutableLiveData("")
    private val _currentCid = MutableLiveData("")

    // 添加状态管理
    private val _locationStatus = MutableLiveData("准备获取位置...")
    private val _cellStatus = MutableLiveData("准备获取基站信息...")

    // 添加超时处理
    private var locationTimeoutHandler: Handler? = null
    private var cellTimeoutHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appInfo = intent.getSerializableExtra("appInfo") as? AppInfo ?: return
        title = appInfo.title
        isDingTalk = PkgConfig.pkg_dingtalk == appInfo.packageName
        tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        lm = getSystemService(LOCATION_SERVICE) as LocationManager

        sp = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE)

        setContent { Container() }

        requestPermissions()
    }

    @Composable
    fun Container() {
        val prefix = appInfo.packageName + "_"
        val defaultLatitude = if (isDingTalk) "" else Constants.DEFAULT_LATITUDE
        val defaultLongitude = if (isDingTalk) "" else Constants.DEFAULT_LONGITUDE

        var latitude by remember {
            mutableStateOf(sp.getString(prefix + "latitude", null) ?: defaultLatitude)
        }
        var longitude by remember {
            mutableStateOf(sp.getString(prefix + "longitude", null) ?: defaultLongitude)
        }
        var lac by remember {
            CellLocationHelper.getLac(sp, prefix).let {
                mutableStateOf(if (it == Constants.DEFAULT_LAC) "" else it.toString())
            }
        }
        var cid by remember {
            CellLocationHelper.getCid(sp, prefix).let {
                mutableStateOf(if (it == Constants.DEFAULT_CID) "" else it.toString())
            }
        }
        var isChecked by remember {
            mutableStateOf(sp.getBoolean(appInfo.packageName, false))
        }

        // 历史记录相关状态
        var showHistoryDialog by remember { mutableStateOf(false) }
        var showSaveDialog by remember { mutableStateOf(false) }
        var showEditDialog by remember { mutableStateOf(false) }
        var editingHistory by remember { mutableStateOf<LocationHistory?>(null) }
        var historyList by remember { mutableStateOf(LocationHistoryManager.getHistoryList()) }

        val currentLatitude by _currentLatitude.observeAsState("")
        val currentLongitude by _currentLongitude.observeAsState("")
        val currentLac by _currentLac.observeAsState("")
        val currentCid by _currentCid.observeAsState("")

        // 添加状态观察
        val locationStatus by _locationStatus.observeAsState("准备获取位置...")
        val cellStatus by _cellStatus.observeAsState("准备获取基站信息...")

        AppTheme {
            Column(
                Modifier
                    .padding(15.dp, 0.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 历史记录按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { showHistoryDialog = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("历史记录")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("保存当前")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.gps_location),
                    modifier = Modifier.padding(0.dp, 16.dp),
                    color = colorResource(R.color.textColorPrimary)
                )

                // 添加GPS状态指示 - 修复 Color.Orange 问题
                Text(
                    text = "状态: $locationStatus",
                    fontSize = 12.sp,
                    color = if (currentLatitude.isNotEmpty()) Color.Green else Color(0xFFFF9800), // 使用橙色的十六进制值
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text(text = "latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(5.dp))
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text(text = "longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.current_latitude, currentLatitude))
                        Text(text = stringResource(R.string.current_longitude, currentLongitude))
                    }
                    if (currentLatitude.isNotEmpty())
                        TextButton(onClick = {
                            latitude = currentLatitude
                            longitude = currentLongitude
                        }) {
                            Text(
                                text = stringResource(R.string.auto_fill),
                                color = colorResource(R.color.textColorPrimary)
                            )
                        }
                }

                Text(
                    text = stringResource(R.string.cell_location),
                    modifier = Modifier.padding(0.dp, 16.dp),
                    color = colorResource(R.color.textColorPrimary)
                )

                // 添加基站状态指示 - 修复 Color.Orange 问题
                Text(
                    text = "状态: $cellStatus",
                    fontSize = 12.sp,
                    color = if (currentLac.isNotEmpty()) Color.Green else Color(0xFFFF9800), // 使用橙色的十六进制值
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = lac,
                    onValueChange = { lac = it },
                    label = { Text(text = "Area Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(5.dp))
                OutlinedTextField(
                    value = cid,
                    onValueChange = { cid = it },
                    label = { Text(text = "Cell Identity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.current_lac, currentLac))
                        Text(text = stringResource(R.string.current_cid, currentCid))
                    }
                    if (currentLac.isNotEmpty())
                        TextButton(onClick = {
                            lac = currentLac
                            cid = currentCid
                        }) {
                            Text(
                                text = stringResource(R.string.auto_fill),
                                color = colorResource(R.color.textColorPrimary)
                            )
                        }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.height(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.open_location_hook),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = isChecked, onCheckedChange = { isChecked = it })
                }
                Row(Modifier.padding(0.dp, 16.dp)) {
                    Button(
                        onClick = {
                            sp.edit().putString(prefix + "latitude", latitude)
                                .putString(prefix + "longitude", longitude)
                                .putLong(prefix + "lac", parseLong(lac))
                                .putLong(prefix + "cid", parseLong(cid))
                                .putLong(prefix + "time", System.currentTimeMillis())
                                .putBoolean(appInfo.packageName, isChecked)
                                .commit()
                            SharedPreferencesHelper.makeWorldReadable(sp)
                            Toast.makeText(
                                applicationContext,
                                R.string.save_success,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF00975C)),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent()
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                intent.data = Uri.fromParts("package", appInfo.packageName, null)
                                startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE9686B)),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                    ) {
                        Text(text = stringResource(R.string.reboot_app))
                    }
                }
            }
        }

        // 历史记录对话框
        if (showHistoryDialog) {
            HistoryDialog(
                historyList = historyList,
                onDismiss = { showHistoryDialog = false },
                onItemClick = { history ->
                    latitude = history.latitude
                    longitude = history.longitude
                    lac = history.lac
                    cid = history.cid
                    showHistoryDialog = false
                },
                onEditClick = { history ->
                    editingHistory = history
                    showEditDialog = true
                },
                onDeleteClick = { history ->
                    LocationHistoryManager.deleteHistory(history.id)
                    historyList = LocationHistoryManager.getHistoryList()
                }
            )
        }

        // 保存对话框
        if (showSaveDialog) {
            SaveHistoryDialog(
                currentLatitude = latitude,
                currentLongitude = longitude,
                currentLac = lac,
                currentCid = cid,
                onDismiss = { showSaveDialog = false },
                onSave = { name ->
                    val history = LocationHistory(
                        name = name,
                        latitude = latitude,
                        longitude = longitude,
                        lac = lac,
                        cid = cid
                    )
                    LocationHistoryManager.saveHistory(history)
                    historyList = LocationHistoryManager.getHistoryList()
                    showSaveDialog = false
                    Toast.makeText(this@RimetActivity, "保存成功", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 编辑对话框
        if (showEditDialog && editingHistory != null) {
            EditHistoryDialog(
                history = editingHistory!!,
                onDismiss = {
                    showEditDialog = false
                    editingHistory = null
                },
                onSave = { newName ->
                    LocationHistoryManager.updateHistoryName(editingHistory!!.id, newName)
                    historyList = LocationHistoryManager.getHistoryList()
                    showEditDialog = false
                    editingHistory = null
                    Toast.makeText(this@RimetActivity, "修改成功", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    @Composable
    fun HistoryDialog(
        historyList: List<LocationHistory>,
        onDismiss: () -> Unit,
        onItemClick: (LocationHistory) -> Unit,
        onEditClick: (LocationHistory) -> Unit,
        onDeleteClick: (LocationHistory) -> Unit
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "历史记录",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (historyList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无历史记录")
                        }
                    } else {
                        LazyColumn {
                            items(historyList) { history ->
                                HistoryItem(
                                    history = history,
                                    onItemClick = { onItemClick(history) },
                                    onEditClick = { onEditClick(history) },
                                    onDeleteClick = { onDeleteClick(history) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HistoryItem(
        history: LocationHistory,
        onItemClick: () -> Unit,
        onEditClick: () -> Unit,
        onDeleteClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onItemClick() },
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = history.name.ifEmpty { "未命名" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "GPS: ${history.latitude}, ${history.longitude}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "基站: LAC=${history.lac}, CID=${history.cid}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = SimpleDateFormat(
                        "MM-dd HH:mm",
                        Locale.getDefault()
                    ).format(Date(history.updateTime)),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }

    @Composable
    fun SaveHistoryDialog(
        currentLatitude: String,
        currentLongitude: String,
        currentLac: String,
        currentCid: String,
        onDismiss: () -> Unit,
        onSave: (String) -> Unit
    ) {
        var name by remember { mutableStateOf("") }

        Dialog(onDismissRequest = onDismiss) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "保存当前位置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("备注名称") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("请输入备注名称") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "当前信息：",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "GPS: $currentLatitude, $currentLongitude",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "基站: LAC=$currentLac, CID=$currentCid",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSave(name) },
                            enabled = currentLatitude.isNotEmpty() || currentLongitude.isNotEmpty() ||
                                    currentLac.isNotEmpty() || currentCid.isNotEmpty()
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EditHistoryDialog(
        history: LocationHistory,
        onDismiss: () -> Unit,
        onSave: (String) -> Unit
    ) {
        var name by remember { mutableStateOf(history.name) }

        Dialog(onDismissRequest = onDismiss) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "编辑备注",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("备注名称") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onSave(name) }) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    private fun parseLong(str: String): Long {
        return try {
            str.toLong()
        } catch (e: Exception) {
            -1
        }
    }

    override fun finish() {
        stopLocation()
        super.finish()
    }

    // 优化权限请求 - 修复权限检查逻辑
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        } else {
            startLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (allGranted) {
                startLocation()
            } else {
                Toast.makeText(this, "需要位置权限才能获取位置信息", Toast.LENGTH_LONG).show()
                _locationStatus.value = "权限被拒绝"
                _cellStatus.value = "权限被拒绝"
            }
        }
    }

    // 优化位置获取
    private fun startLocation() {
        startGPSLocation()
        startCellLocationMonitoring()
    }

    private fun startGPSLocation() {
        if (!checkLocationPermission()) return

        _locationStatus.value = "正在获取GPS位置..."

        // 检查GPS是否开启
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            _locationStatus.value = "GPS未开启"
            Toast.makeText(this, "请开启GPS定位服务", Toast.LENGTH_LONG).show()

            // 引导用户开启GPS
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        try {
            // 先尝试获取最后已知位置
            val lastKnownLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnownLocation != null) {
                gpsListener.onLocationChanged(lastKnownLocation)
                _locationStatus.value = "使用缓存位置"
            }

            // 请求位置更新
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L, // 最小时间间隔5秒
                10f,   // 最小距离10米
                gpsListener
            )

            // 同时尝试网络定位作为备选
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    10f,
                    gpsListener
                )
            }

            // 设置超时处理
            locationTimeoutHandler = Handler(Looper.getMainLooper())
            locationTimeoutHandler?.postDelayed({
                if (_currentLatitude.value.isNullOrEmpty()) {
                    _locationStatus.value = "GPS定位超时"
                    Toast.makeText(
                        this@RimetActivity,
                        "GPS定位超时，请确保在室外空旷环境",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, 30000) // 30秒超时

        } catch (e: SecurityException) {
            _locationStatus.value = "权限不足"
            Toast.makeText(this, "获取位置权限失败", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            _locationStatus.value = "GPS获取失败: ${e.message}"
            Toast.makeText(this, "GPS获取失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCellLocationMonitoring() {
        if (!checkLocationPermission()) return

        _cellStatus.value = "正在获取基站信息..."

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                tm.listen(listener, PhoneStateListener.LISTEN_CELL_LOCATION)

                // 尝试获取当前基站位置
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    val cellLocation = tm.cellLocation
                    if (cellLocation is GsmCellLocation) {
                        listener.onCellLocationChanged(cellLocation)
                    }
                }
            } else {
                tm.listen(listener, PhoneStateListener.LISTEN_CELL_INFO)

                // 尝试获取当前基站信息
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    val cellInfoList = tm.allCellInfo
                    if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                        listener.onCellInfoChanged(cellInfoList)
                    }
                }
            }

            // 设置基站信息获取超时
            cellTimeoutHandler = Handler(Looper.getMainLooper())
            cellTimeoutHandler?.postDelayed({
                if (_currentLac.value.isNullOrEmpty()) {
                    _cellStatus.value = "基站信息获取超时"
                    Toast.makeText(
                        this@RimetActivity,
                        "基站信息获取超时，请检查移动网络",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, 15000) // 15秒超时

        } catch (e: SecurityException) {
            _cellStatus.value = "权限不足"
            Toast.makeText(this, "获取基站信息权限失败", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            _cellStatus.value = "基站信息获取失败: ${e.message}"
            Toast.makeText(this, "基站信息获取失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // 优化监听器
    private var listener: PhoneStateListener = object : PhoneStateListener() {
        override fun onCellLocationChanged(location: CellLocation) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return

            try {
                if (location is GsmCellLocation) {
                    l = location
                    if (l.lac != -1 && l.cid != -1) {
                        _currentLac.value = l.lac.toString()
                        _currentCid.value = l.cid.toString()
                        _cellStatus.value = "基站信息获取成功"
                        cellTimeoutHandler?.removeCallbacksAndMessages(null)
                    } else {
                        _cellStatus.value = "基站信息无效"
                    }
                }
            } catch (e: Exception) {
                _cellStatus.value = "基站信息解析失败"
                Toast.makeText(
                    this@RimetActivity,
                    "基站信息解析失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

            try {
                if (cellInfo == null || cellInfo.isEmpty()) {
                    _cellStatus.value = "未找到基站信息"
                    return
                }

                // 寻找注册的基站
                val registeredCell = cellInfo.find { it.isRegistered } ?: cellInfo[0]

                when (val cellIdentity = registeredCell.cellIdentity) {
                    is CellIdentityGsm -> {
                        if (cellIdentity.lac != CellInfo.UNAVAILABLE && cellIdentity.cid != CellInfo.UNAVAILABLE) {
                            _currentLac.value = cellIdentity.lac.toString()
                            _currentCid.value = cellIdentity.cid.toString()
                            _cellStatus.value = "GSM基站信息获取成功"
                            cellTimeoutHandler?.removeCallbacksAndMessages(null)
                        }
                    }

                    is CellIdentityLte -> {
                        if (cellIdentity.tac != CellInfo.UNAVAILABLE && cellIdentity.ci != CellInfo.UNAVAILABLE) {
                            _currentLac.value = cellIdentity.tac.toString()
                            _currentCid.value = cellIdentity.ci.toString()
                            _cellStatus.value = "LTE基站信息获取成功"
                            cellTimeoutHandler?.removeCallbacksAndMessages(null)
                        }
                    }

                    is CellIdentityNr -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (cellIdentity.tac != CellInfo.UNAVAILABLE &&
                                cellIdentity.nci != CellInfo.UNAVAILABLE.toLong()) {
                                _currentLac.value = cellIdentity.tac.toString()
                                _currentCid.value = cellIdentity.nci.toString()
                                _cellStatus.value = "5G基站信息获取成功"
                                cellTimeoutHandler?.removeCallbacksAndMessages(null)
                            }
                        }
                    }

                    else -> {
                        _cellStatus.value = "不支持的基站类型"
                    }
                }
            } catch (e: Exception) {
                _cellStatus.value = "基站信息解析失败"
                Toast.makeText(
                    this@RimetActivity,
                    "基站信息解析失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private var gpsListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            try {
                gpsL = location
                _currentLatitude.value = location.latitude.toString()
                _currentLongitude.value = location.longitude.toString()
                _locationStatus.value = "GPS定位成功 (精度: ${location.accuracy.toInt()}m)"

                // 清除超时处理
                locationTimeoutHandler?.removeCallbacksAndMessages(null)

                // 获取到位置后可以停止频繁更新
                if (location.accuracy < 50) { // 精度小于50米时停止频繁更新
                    lm.removeUpdates(this)
                }
            } catch (e: Exception) {
                _locationStatus.value = "位置信息处理失败"
                Toast.makeText(
                    this@RimetActivity,
                    "位置信息处理失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            when (status) {
                LocationProvider.OUT_OF_SERVICE -> _locationStatus.value = "GPS服务不可用"
                LocationProvider.TEMPORARILY_UNAVAILABLE -> _locationStatus.value = "GPS暂时不可用"
                LocationProvider.AVAILABLE -> _locationStatus.value = "GPS服务可用"
            }
        }

        override fun onProviderEnabled(provider: String) {
            _locationStatus.value = "GPS已启用"
        }

        override fun onProviderDisabled(provider: String) {
            _locationStatus.value = "GPS已禁用"
            Toast.makeText(this@RimetActivity, "GPS已被禁用，请在设置中开启", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun stopLocation() {
        try {
            tm.listen(listener, PhoneStateListener.LISTEN_NONE)
            lm.removeUpdates(gpsListener)
            locationTimeoutHandler?.removeCallbacksAndMessages(null)
            cellTimeoutHandler?.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopLocation()
        super.onDestroy()
    }
}