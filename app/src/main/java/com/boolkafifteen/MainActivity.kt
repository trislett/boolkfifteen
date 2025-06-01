package com.boolkafifteen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import com.canhub.cropper.CropImageContract // For the launcher
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView // <<< CRUCIAL IMPORT FOR CropImageView.Guidelines
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.random.Random

enum class DisplayMode { NUMBERS_ONLY, IMAGE_ONLY, NUMBERS_AND_IMAGE }
enum class Difficulty { EASY, MEDIUM, HARD }

class MainActivity : AppCompatActivity() {

    private lateinit var gridLayoutBoard: GridLayout
    private lateinit var buttonShuffle: Button
    private lateinit var textViewStatus: TextView
    private lateinit var textViewMoves: TextView
    private lateinit var textViewTimer: TextView
    private lateinit var buttonToggleMode: Button
    private lateinit var buttonToggleDifficulty: Button
    private lateinit var buttonLoadCustomImage: Button

    private val gridSize = 4
    private var tiles = IntArray(gridSize * gridSize)
    private var buttons = mutableListOf<Button>()
    private var blankPos = gridSize * gridSize - 1
    private var moveCount = 0

    private var currentDisplayMode = DisplayMode.NUMBERS_ONLY
    private var currentDifficulty = Difficulty.HARD
    private var isGameWon = false

    private var imagePieces = mutableListOf<BitmapDrawable?>()
    private var fullPuzzleImage: Bitmap? = null
    private var defaultPuzzleImageLoaded = false
    private var customImageUriString: String? = null

    private val timerHandler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var timeInMilliseconds = 0L
    private var timeSwapBuff = 0L
    private var updateTime = 0L
    private var isTimerRunning = false
    private var gameStarted = false

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var cropImageLauncher: ActivityResultLauncher<CropImageContractOptions>

    companion object {
        private const val TAG = "BoolkaFifteenGame"
        private const val EASY_SHUFFLE_MOVES = 100
        private const val MEDIUM_SHUFFLE_MOVES = 500
        private const val HARD_SHUFFLE_MOVES = 5000

        private const val KEY_TILES = "key_tiles"
        private const val KEY_MOVE_COUNT = "key_move_count"
        private const val KEY_DISPLAY_MODE = "key_display_mode"
        private const val KEY_DIFFICULTY = "key_difficulty"
        private const val KEY_GAME_WON = "key_game_won"
        private const val KEY_GAME_STARTED = "key_game_started"
        private const val KEY_TIMER_RUNNING = "key_timer_running"
        private const val KEY_TIME_SWAP_BUFF = "key_time_swap_buff"
        private const val KEY_START_TIME = "key_start_time"
        private const val KEY_CURRENT_ELAPSED_TIME = "key_current_elapsed_time" // For timer state
        private const val KEY_DEFAULT_IMAGE_LOADED = "key_default_image_loaded"
        private const val KEY_CUSTOM_IMAGE_URI = "key_custom_image_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate started. savedInstanceState is ${if (savedInstanceState == null) "null" else "NOT null"}")

        gridLayoutBoard = findViewById(R.id.gridLayoutBoard)
        buttonShuffle = findViewById(R.id.buttonShuffle)
        textViewStatus = findViewById(R.id.textViewStatus)
        textViewMoves = findViewById(R.id.textViewMoves)
        textViewTimer = findViewById(R.id.textViewTimer)
        buttonToggleMode = findViewById(R.id.buttonToggleMode)
        buttonToggleDifficulty = findViewById(R.id.buttonToggleDifficulty)
        buttonLoadCustomImage = findViewById(R.id.buttonLoadCustomImage)

        setupImagePickerLauncher()
        setupCropImageLauncher()

        Log.d(TAG, "Views and Launchers initialized")

        if (savedInstanceState != null) {
            Log.d(TAG, "Restoring state from savedInstanceState")
            restoreState(savedInstanceState)
            if (fullPuzzleImage != null) {
                splitImagePiecesFromBitmap(fullPuzzleImage)
            } else if (customImageUriString != null) {
                try {
                    val uri = Uri.parse(customImageUriString)
                    fullPuzzleImage = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                    splitImagePiecesFromBitmap(fullPuzzleImage)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reloading custom image from URI: $e")
                    loadDefaultAndSplitImage()
                }
            } else {
                loadDefaultAndSplitImage()
            }
        } else {
            Log.d(TAG, "No saved instance state, loading default image.")
            loadDefaultAndSplitImage()
        }

        initializeBoard(savedInstanceState == null)

        buttonShuffle.setOnClickListener {
            Log.d(TAG, "Shuffle button clicked")
            isGameWon = false
            textViewStatus.text = ""
            gameStarted = false
            stopTimer()
            resetTimerDisplay()
            resetMoveCounter()
            customImageUriString = null
            loadDefaultAndSplitImage()
            shuffleBoard()
        }

        buttonToggleMode.setOnClickListener {
            currentDisplayMode = when (currentDisplayMode) {
                DisplayMode.NUMBERS_ONLY -> DisplayMode.IMAGE_ONLY
                DisplayMode.IMAGE_ONLY -> DisplayMode.NUMBERS_AND_IMAGE
                DisplayMode.NUMBERS_AND_IMAGE -> DisplayMode.NUMBERS_ONLY
            }
            updateDisplayModeButtonText()
            updateBoardUI()
        }

        buttonToggleDifficulty.setOnClickListener {
            currentDifficulty = when (currentDifficulty) {
                Difficulty.EASY -> Difficulty.MEDIUM
                Difficulty.MEDIUM -> Difficulty.HARD
                Difficulty.HARD -> Difficulty.EASY
            }
            updateDifficultyButtonText()
            Log.d(TAG, "Difficulty changed to: $currentDifficulty")
        }

        buttonLoadCustomImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        updateDisplayModeButtonText()
        updateDifficultyButtonText()
        updateMovesDisplay()

        if (savedInstanceState == null) {
            resetTimerDisplay()
        } else {
            if (isTimerRunning) {
                timerHandler.post(timerRunnable) // Re-post runnable if it was running
                Log.d(TAG, "Timer was running, re-posting runnable.")
            } else {
                updateTimerDisplayFromSavedValues()
            }
        }
        Log.d(TAG, "onCreate finished")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "onSaveInstanceState called")
        outState.putIntArray(KEY_TILES, tiles)
        outState.putInt(KEY_MOVE_COUNT, moveCount)
        outState.putString(KEY_DISPLAY_MODE, currentDisplayMode.name)
        outState.putString(KEY_DIFFICULTY, currentDifficulty.name)
        outState.putBoolean(KEY_GAME_WON, isGameWon)
        outState.putBoolean(KEY_GAME_STARTED, gameStarted)
        outState.putBoolean(KEY_TIMER_RUNNING, isTimerRunning)
        outState.putLong(KEY_TIME_SWAP_BUFF, timeSwapBuff)
        outState.putLong(KEY_START_TIME, startTime)
        if (isTimerRunning) { // Save current elapsed time for accurate restoration
            outState.putLong(KEY_CURRENT_ELAPSED_TIME, System.currentTimeMillis() - startTime)
        }
        outState.putBoolean(KEY_DEFAULT_IMAGE_LOADED, defaultPuzzleImageLoaded)
        customImageUriString?.let { outState.putString(KEY_CUSTOM_IMAGE_URI, it) }
    }

    private fun restoreState(savedInstanceState: Bundle) {
        tiles = savedInstanceState.getIntArray(KEY_TILES) ?: IntArray(gridSize * gridSize)
        moveCount = savedInstanceState.getInt(KEY_MOVE_COUNT)
        currentDisplayMode = DisplayMode.valueOf(savedInstanceState.getString(KEY_DISPLAY_MODE) ?: DisplayMode.NUMBERS_ONLY.name)
        currentDifficulty = Difficulty.valueOf(savedInstanceState.getString(KEY_DIFFICULTY) ?: Difficulty.HARD.name)
        isGameWon = savedInstanceState.getBoolean(KEY_GAME_WON)
        gameStarted = savedInstanceState.getBoolean(KEY_GAME_STARTED)
        isTimerRunning = savedInstanceState.getBoolean(KEY_TIMER_RUNNING)
        timeSwapBuff = savedInstanceState.getLong(KEY_TIME_SWAP_BUFF)
        startTime = savedInstanceState.getLong(KEY_START_TIME)

        if (isTimerRunning) {
            // This helps if we want to immediately update UI before runnable ticks,
            // but timerRunnable will use startTime and timeSwapBuff primarily.
            timeInMilliseconds = savedInstanceState.getLong(KEY_CURRENT_ELAPSED_TIME, 0L)
        } else {
            timeInMilliseconds = 0L
        }

        defaultPuzzleImageLoaded = savedInstanceState.getBoolean(KEY_DEFAULT_IMAGE_LOADED, true)
        customImageUriString = savedInstanceState.getString(KEY_CUSTOM_IMAGE_URI)
        blankPos = tiles.indexOf(0)
    }

    private fun updateTimerDisplayFromSavedValues() {
        updateTime = timeSwapBuff // timeInMilliseconds is 0 if timer wasn't running
        val secs = (updateTime / 1000).toInt()
        val mins = secs / 60
        val displaySecs = secs % 60
        textViewTimer.text = getString(R.string.timer_format, mins, displaySecs)
    }

    // --- DEFINITIONS FOR THE HELPER FUNCTIONS ---
    private fun updateDisplayModeButtonText() {
        buttonToggleMode.text = when (currentDisplayMode) {
            DisplayMode.NUMBERS_ONLY -> getString(R.string.display_mode_numbers)
            DisplayMode.IMAGE_ONLY -> getString(R.string.display_mode_image)
            DisplayMode.NUMBERS_AND_IMAGE -> getString(R.string.display_mode_both)
        }
    }

    private fun updateDifficultyButtonText() {
        buttonToggleDifficulty.text = when (currentDifficulty) {
            Difficulty.EASY -> getString(R.string.difficulty_easy)
            Difficulty.MEDIUM -> getString(R.string.difficulty_medium)
            Difficulty.HARD -> getString(R.string.difficulty_hard)
        }
    }
    // --- END OF DEFINITIONS FOR HELPER FUNCTIONS ---

    private fun setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                Log.d(TAG, "Image picked: $it")
                val cropOptions = CropImageContractOptions(
                    uri = it,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON, // Uses imported CropImageView
                        aspectRatioX = 1,
                        aspectRatioY = 1,
                        fixAspectRatio = true,
                        outputCompressQuality = 80,
                        outputRequestWidth = 800,
                        outputRequestHeight = 800,
                        outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE
                    )
                )
                cropImageLauncher.launch(cropOptions)
            } ?: Log.d(TAG, "Image picking cancelled or failed.")
        }
    }

    private fun setupCropImageLauncher() {
        cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                result.uriContent?.let { croppedUri ->
                    Log.d(TAG, "Image cropped successfully: $croppedUri")
                    customImageUriString = croppedUri.toString()
                    defaultPuzzleImageLoaded = false
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, croppedUri)
                        if (bitmap != null) {
                            fullPuzzleImage = bitmap
                            splitImagePiecesFromBitmap(fullPuzzleImage)
                            currentDisplayMode = DisplayMode.IMAGE_ONLY
                            updateDisplayModeButtonText() // Call here
                            shuffleBoard()
                            Toast.makeText(this, "Custom image loaded!", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e(TAG, "Failed to decode cropped bitmap.")
                            Toast.makeText(this, "Failed to load cropped image.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error loading cropped image: ${e.message}", e)
                        Toast.makeText(this, "Error loading image.", Toast.LENGTH_SHORT).show()
                    }
                } ?: Log.e(TAG, "Cropped URI is null.")
            } else {
                val error = result.error
                Log.e(TAG, "Image cropping failed: ${error?.message}", error)
                Toast.makeText(this, "Image cropping failed or cancelled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDefaultAndSplitImage() {
        try {
            val inputStream = resources.openRawResource(R.drawable.my_puzzle_image)
            val defaultBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            fullPuzzleImage = defaultBitmap
            splitImagePiecesFromBitmap(fullPuzzleImage)
            defaultPuzzleImageLoaded = true
            customImageUriString = null
            Log.d(TAG, "Default image loaded and split.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default image: ${e.message}")
            fullPuzzleImage = null
            defaultPuzzleImageLoaded = false
        }
    }

    private fun splitImagePiecesFromBitmap(sourceBitmap: Bitmap?) {
        imagePieces.clear()
        sourceBitmap?.let { bmp ->
            val pieceWidth = bmp.width / gridSize
            val pieceHeight = bmp.height / gridSize
            if (pieceWidth == 0 || pieceHeight == 0) {
                Log.e(TAG, "Image piece dimensions are zero for splitting.")
                fullPuzzleImage = null
                return
            }
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    try {
                        val piece = Bitmap.createBitmap(bmp, col * pieceWidth, row * pieceHeight, pieceWidth, pieceHeight)
                        imagePieces.add(BitmapDrawable(resources, piece))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating bitmap piece at $row, $col: ${e.message}")
                    }
                }
            }
            Log.d(TAG, "Bitmap split into ${imagePieces.size} pieces.")
        } ?: Log.e(TAG, "Source bitmap for splitting is null.")
    }

    private val timerRunnable: Runnable = object : Runnable {
        override fun run() {
            timeInMilliseconds = System.currentTimeMillis() - startTime
            updateTime = timeSwapBuff + timeInMilliseconds
            val secs = (updateTime / 1000).toInt()
            val mins = secs / 60
            val displaySecs = secs % 60
            textViewTimer.text = getString(R.string.timer_format, mins, displaySecs)
            timerHandler.postDelayed(this, 500)
        }
    }

    private fun startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis()
            // timeSwapBuff should be 0 if we are truly starting fresh.
            // If resuming a paused game, timeSwapBuff would hold previous time.
            // For this game, shuffle resets timer, so timeSwapBuff is 0.
            timerHandler.post(timerRunnable)
            isTimerRunning = true
            Log.d(TAG, "Timer started.")
        }
    }

    private fun stopTimer() {
        if (isTimerRunning) {
            // Capture elapsed time for the current segment before stopping
            timeSwapBuff += (System.currentTimeMillis() - startTime)
            timerHandler.removeCallbacks(timerRunnable)
            isTimerRunning = false
            Log.d(TAG, "Timer stopped. timeSwapBuff: $timeSwapBuff")
        }
    }

    private fun resetTimerDisplay() {
        timeSwapBuff = 0L
        timeInMilliseconds = 0L // Not strictly needed here as updateTime will use timeSwapBuff
        updateTime = 0L
        startTime = 0L // Reset startTime as well
        textViewTimer.text = getString(R.string.timer_format, 0, 0)
        Log.d(TAG, "Timer display reset.")
    }

    private fun resetMoveCounter() {
        moveCount = 0
        updateMovesDisplay()
    }

    private fun incrementMoveCounter() {
        moveCount++
        updateMovesDisplay()
    }

    private fun updateMovesDisplay() {
        textViewMoves.text = getString(R.string.moves_format, moveCount)
    }

    private fun initializeBoard(performShuffle: Boolean = true) {
        Log.d(TAG, "initializeBoard started, performShuffle: $performShuffle")
        buttons.clear()
        gridLayoutBoard.removeAllViews()

        if (performShuffle) { // Only reset these if it's a new game/shuffle
            isGameWon = false
            resetMoveCounter()
            resetTimerDisplay()
            gameStarted = false
        }


        gridLayoutBoard.post {
            Log.d(TAG, "gridLayoutBoard.post running")
            val gridWidth = gridLayoutBoard.width
            val gridHeight = gridLayoutBoard.height

            if (gridWidth == 0 || gridHeight == 0) {
                Log.e(TAG, "GridLayout dimensions are zero.")
                textViewStatus.text = "Error: Could not size game board."
                return@post
            }

            val cellWidth = gridWidth / gridSize
            val cellHeight = gridHeight / gridSize
            val buttonMarginPx = dpToPx(1)
            val calculatedButtonWidth = cellWidth - (buttonMarginPx * 2)
            val calculatedButtonHeight = cellHeight - (buttonMarginPx * 2)
            val buttonSize = kotlin.math.min(calculatedButtonWidth, calculatedButtonHeight)

            if (buttonSize <= 0) {
                Log.e(TAG, "Button size is zero or negative.")
                textViewStatus.text = "Error: Button size too small."
                return@post
            }

            for (i in 0 until gridSize * gridSize) {
                val button = Button(this)
                val params = GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(buttonMarginPx, buttonMarginPx, buttonMarginPx, buttonMarginPx)
                    rowSpec = GridLayout.spec(i / gridSize, 1f)
                    columnSpec = GridLayout.spec(i % gridSize, 1f)
                }
                button.layoutParams = params
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                button.setPadding(0, 0, 0, 0)
                button.setOnClickListener { onTileClick(buttons.indexOf(it)) }
                buttons.add(button)
                gridLayoutBoard.addView(button)
            }
            Log.d(TAG, "All ${buttons.size} buttons created and added to grid.")

            if (performShuffle) {
                shuffleBoard()
            } else {
                updateBoardUI()
                if (isGameWon) {
                    textViewStatus.text = getString(R.string.win_message)
                }
            }
        }
        Log.d(TAG, "initializeBoard finished (post block scheduled)")
    }

    private fun updateBoardUI() {
        Log.d(TAG, "updateBoardUI started. DisplayMode: $currentDisplayMode, GameWon: $isGameWon")
        if (buttons.isEmpty() || buttons.size != gridSize * gridSize) {
            Log.e(TAG, "updateBoardUI: Buttons not initialized correctly. Aborting.")
            return
        }
        val imageAvailable = fullPuzzleImage != null && imagePieces.size == gridSize * gridSize
        val outlineColor = ContextCompat.getColor(this, R.color.tile_outline_color)

        for (i in 0 until gridSize * gridSize) {
            val button = buttons[i]
            val tileValue = tiles[i]

            button.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

            if (isGameWon && i == gridSize * gridSize - 1) {
                button.isClickable = false
                button.setTextColor(ContextCompat.getColor(this, R.color.tile_text_color))
                when (currentDisplayMode) {
                    DisplayMode.NUMBERS_ONLY -> {
                        button.text = "16"
                        button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                    }
                    DisplayMode.IMAGE_ONLY -> {
                        button.text = ""
                        if (imageAvailable && imagePieces.indices.contains(gridSize * gridSize - 1)) {
                             button.background = imagePieces[gridSize * gridSize - 1]
                        } else {
                            button.text = "16"
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                    DisplayMode.NUMBERS_AND_IMAGE -> {
                        button.text = "16"
                        if (imageAvailable && imagePieces.indices.contains(gridSize * gridSize - 1)) {
                            button.background = imagePieces[gridSize * gridSize - 1]
                            button.setShadowLayer(2f, 1f, 1f, outlineColor)
                        } else {
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                }
            } else if (tileValue == 0) {
                button.text = ""
                button.background = ContextCompat.getDrawable(this, R.drawable.blank_tile_background)
                button.isClickable = false
                blankPos = i
            } else {
                button.isClickable = !isGameWon
                button.setTextColor(ContextCompat.getColor(this, R.color.tile_text_color))
                when (currentDisplayMode) {
                    DisplayMode.NUMBERS_ONLY -> {
                        button.text = tileValue.toString()
                        button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                    }
                    DisplayMode.IMAGE_ONLY -> {
                        button.text = ""
                        if (imageAvailable && imagePieces.indices.contains(tileValue - 1)) {
                            button.background = imagePieces[tileValue - 1]
                        } else {
                            button.text = tileValue.toString()
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                    DisplayMode.NUMBERS_AND_IMAGE -> {
                        button.text = tileValue.toString()
                        if (imageAvailable && imagePieces.indices.contains(tileValue - 1)) {
                            button.background = imagePieces[tileValue - 1]
                            button.setShadowLayer(2f, 1f, 1f, outlineColor)
                        } else {
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                }
            }
        }
        Log.d(TAG, "updateBoardUI finished. Current blankPos: $blankPos. Tiles: ${tiles.contentToString()}")
    }

    private fun onTileClick(clickedPhysicalPos: Int) {
        if (isGameWon) {
            Log.d(TAG, "Game won, click ignored.")
            return
        }
        Log.d(TAG, "onTileClick: clickedPhysicalPos=$clickedPhysicalPos")

        if (!gameStarted) {
            gameStarted = true
            startTimer()
        }

        val blankPhysicalPos = tiles.indexOf(0)
        val blankRow = blankPhysicalPos / gridSize
        val blankCol = blankPhysicalPos % gridSize
        val clickedRow = clickedPhysicalPos / gridSize
        val clickedCol = clickedPhysicalPos % gridSize

        val isAdjacent = (kotlin.math.abs(blankRow - clickedRow) == 1 && blankCol == clickedCol) ||
                         (kotlin.math.abs(blankCol - clickedCol) == 1 && blankRow == clickedRow)

        if (isAdjacent) {
            tiles[blankPhysicalPos] = tiles[clickedPhysicalPos]
            tiles[clickedPhysicalPos] = 0
            incrementMoveCounter()
            updateBoardUI()
            checkWinCondition()
        }
    }

    private fun shuffleBoard() {
        Log.d(TAG, "shuffleBoard started - Difficulty: $currentDifficulty")
        isGameWon = false // Ensure game is not won when shuffling

        val tempTilesArray = IntArray(gridSize * gridSize) { if (it == gridSize * gridSize - 1) 0 else it + 1 }
        var currentBlankForShuffle = tempTilesArray.indexOf(0)

        val numberOfShuffles = when (currentDifficulty) {
            Difficulty.EASY -> EASY_SHUFFLE_MOVES
            Difficulty.MEDIUM -> MEDIUM_SHUFFLE_MOVES
            Difficulty.HARD -> HARD_SHUFFLE_MOVES
        }
        Log.d(TAG, "Shuffling with $numberOfShuffles moves.")

        for (k in 0 until numberOfShuffles) {
            val possibleMoves = mutableListOf<Int>()
            val blankRow = currentBlankForShuffle / gridSize
            val blankCol = currentBlankForShuffle % gridSize

            if (blankRow > 0) possibleMoves.add(currentBlankForShuffle - gridSize)
            if (blankRow < gridSize - 1) possibleMoves.add(currentBlankForShuffle + gridSize)
            if (blankCol > 0) possibleMoves.add(currentBlankForShuffle - 1)
            if (blankCol < gridSize - 1) possibleMoves.add(currentBlankForShuffle + 1)

            if (possibleMoves.isNotEmpty()) {
                val movePos = possibleMoves[Random.nextInt(possibleMoves.size)]
                tempTilesArray[currentBlankForShuffle] = tempTilesArray[movePos]
                tempTilesArray[movePos] = 0
                currentBlankForShuffle = movePos
            }
        }

        tiles = tempTilesArray
        blankPos = tiles.indexOf(0)

        // Reset game state for the new puzzle
        resetMoveCounter()
        if (gameStarted || isTimerRunning) { // If timer was running or game had started
            stopTimer()
            resetTimerDisplay()
            gameStarted = false
        }
        textViewStatus.text = "" // Clear win/status message
        updateBoardUI()
    }

    private fun checkWinCondition() {
        if (isGameWon) return

        var solved = true
        for (i in 0 until gridSize * gridSize - 1) {
            if (tiles[i] != i + 1) {
                solved = false
                break
            }
        }
        if (solved && tiles[gridSize * gridSize - 1] == 0) {
            Log.d(TAG, "Win condition MET!")
            isGameWon = true
            textViewStatus.text = getString(R.string.win_message)
            stopTimer()
            gameStarted = false
            updateBoardUI()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        Log.d(TAG, "onDestroy: Timer callbacks removed.")
    }
}