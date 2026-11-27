package com.linehider

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * 主设置界面：配置遮罩线的位置、宽度、开关，控制覆盖层服务启停。
 *
 * 覆盖层由 [OverlayService] 提供，本 Activity 通过修改 SharedPreferences
 * 实时驱动覆盖层刷新（服务端监听 prefs 变化）。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appPrefs: AppPreferences
    private lateinit var lineContainer: LinearLayout
    private lateinit var swOverlay: SwitchMaterial
    private lateinit var swBoot: SwitchMaterial
    private lateinit var btnAddLine: MaterialButton
    private lateinit var tvStatus: TextView

    /** 屏幕宽度（像素），用于 X 滑块上限。 */
    private var screenWidth: Int = 1080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appPrefs = AppPreferences(this)
        screenWidth = resources.displayMetrics.widthPixels

        lineContainer = findViewById(R.id.lineContainer)
        swOverlay = findViewById(R.id.swOverlay)
        swBoot = findViewById(R.id.swBoot)
        btnAddLine = findViewById(R.id.btnAddLine)
        tvStatus = findViewById(R.id.tvStatus)

        // 总开关
        swOverlay.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.overlayEnabled = isChecked
            updateStatusText()
            if (isChecked) {
                if (hasOverlayPermission()) OverlayService.start(this)
            } else {
                OverlayService.stop(this)
            }
        }

        // 开机自启
        swBoot.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.bootAutoStart = isChecked
        }

        // 添加线
        btnAddLine.setOnClickListener {
            appPrefs.addLine(screenWidth)
            rebuildLineList()
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到界面时重新检查权限与状态
        swOverlay.isChecked = appPrefs.overlayEnabled
        swBoot.isChecked = appPrefs.bootAutoStart
        updateStatusText()
        rebuildLineList()
        ensureOverlayRunningIfNeeded()
    }

    /** 检查并请求悬浮窗权限。 */
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun ensureOverlayRunningIfNeeded() {
        if (appPrefs.overlayEnabled) {
            if (hasOverlayPermission()) {
                OverlayService.start(this)
            } else {
                // 未授权，引导用户开启
                tvStatus.text = "未授予悬浮窗权限，点击开关以授权"
                tvStatus.setTextColor(0xFFE53935.toInt())
                requestOverlayPermission()
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (hasOverlayPermission() && appPrefs.overlayEnabled) {
                OverlayService.start(this)
                updateStatusText()
            }
        }
    }

    private fun updateStatusText() {
        if (appPrefs.overlayEnabled) {
            tvStatus.text = "遮罩运行中"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            tvStatus.text = "遮罩已关闭"
            tvStatus.setTextColor(getColor(android.R.color.darker_gray))
        }
    }

    /** 重新构建线列表（增删后调用）。 */
    private fun rebuildLineList() {
        lineContainer.removeAllViews()
        val lines = appPrefs.getLines()
        val inflater = LayoutInflater.from(this)
        for (line in lines) {
            val view = inflater.inflate(R.layout.item_line, lineContainer, false)
            bindLineItem(view, line)
            lineContainer.addView(view)
        }
        // 达到上限时禁用添加按钮
        btnAddLine.isEnabled = lines.size < MaskLine.MAX_LINES
        btnAddLine.text = if (lines.size >= MaskLine.MAX_LINES) {
            "已达上限（${MaskLine.MAX_LINES} 条）"
        } else {
            "添加一条线"
        }
    }

    /** 绑定单条线的控件与事件。 */
    private fun bindLineItem(view: View, line: MaskLine) {
        val tvTitle = view.findViewById<TextView>(R.id.tvLineTitle)
        val swEnable = view.findViewById<SwitchMaterial>(R.id.swLineEnable)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)
        val sbX = view.findViewById<SeekBar>(R.id.sbX)
        val sbWidth = view.findViewById<SeekBar>(R.id.sbWidth)
        val tvXValue = view.findViewById<TextView>(R.id.tvXValue)
        val tvWidthValue = view.findViewById<TextView>(R.id.tvWidthValue)
        val rowPosition = view.findViewById<View>(R.id.rowPosition)
        val panelButtons = view.findViewById<LinearLayout>(R.id.panelPositionButtons)
        val btnLeft = view.findViewById<MaterialButton>(R.id.btnLeft)
        val btnRight = view.findViewById<MaterialButton>(R.id.btnRight)

        // 用可变引用跟踪当前线的最新状态，避免两个滑块互相覆盖字段
        var current = line

        tvTitle.text = "线 ${current.id + 1}"
        swEnable.isChecked = current.enabled

        // X 滑块上限为屏宽，进度为当前 x
        sbX.max = screenWidth
        sbX.progress = current.x.coerceIn(0, screenWidth)
        tvXValue.text = current.x.toString()

        // 宽度滑块 1..50
        sbWidth.max = 50
        sbWidth.progress = current.width.coerceIn(1, 50)
        tvWidthValue.text = current.width.toString()

        // 统一的保存：基于 current 修改指定字段后写入 prefs，并同步 current
        fun commit(newLine: MaskLine) {
            current = newLine
            appPrefs.updateLine(current)
        }

        swEnable.setOnCheckedChangeListener { _, isChecked ->
            commit(current.copy(enabled = isChecked))
        }

        sbX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                tvXValue.text = progress.toString()
                commit(current.copy(x = progress))
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        sbWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val w = progress.coerceAtLeast(1)
                tvWidthValue.text = w.toString()
                commit(current.copy(width = w))
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        // 点击位置行：展开/收起左/右微调按钮面板
        rowPosition.setOnClickListener {
            panelButtons.visibility =
                if (panelButtons.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // 左移/右移：单击移动 1px，长按连发
        val handler = Handler(Looper.getMainLooper())
        fun moveBy(delta: Int) {
            val newX = (current.x + delta).coerceIn(0, screenWidth)
            if (newX == current.x) return
            tvXValue.text = newX.toString()
            sbX.progress = newX
            commit(current.copy(x = newX))
        }
        fun attachRepeat(button: MaterialButton, delta: Int) {
            var repeatRunnable: Runnable? = null
            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        moveBy(delta)
                        val r = object : Runnable {
                            override fun run() {
                                moveBy(delta)
                                handler.postDelayed(this, 60)
                            }
                        }
                        repeatRunnable = r
                        handler.postDelayed(r, 400)
                        button.isPressed = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        repeatRunnable?.let { handler.removeCallbacks(it) }
                        repeatRunnable = null
                        button.isPressed = false
                    }
                }
                true
            }
        }
        attachRepeat(btnLeft, -1)
        attachRepeat(btnRight, +1)

        btnDelete.setOnClickListener {
            appPrefs.removeLine(current.id)
            rebuildLineList()
        }
    }

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }
}
