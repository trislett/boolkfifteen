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
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
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
    private var customImageUriString: String? = null // Stores URI of the loaded custom image

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
        private const val HARD_SHUFFLE_MOVES = 2000

        // Keys for onSaveInstanceState
        private const val KEY_TILES = "key_tiles"
        private const val KEY_MOVE_COUNT = "key_move_count"
        private const val KEY_DISPLAY_MODE = "key_display_mode"
        private const val KEY_DIFFICULTY = "key_difficulty"
        private const val KEY_GAME_WON = "key_game_won"
        private const val KEY_GAME_STARTED = "key_game_started"
        private const val KEY_TIMER_RUNNING = "key_timer_running"
        private const val KEY_TIME_SWAP_BUFF = "key_time_swap_buff"
        private const val KEY_START_TIME = "key_start_time"
        private const val KEY_CURRENT_ELAPSED_TIME = "key_current_elapsed_time"
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
            loadImageBasedOnState() // Load appropriate image (custom or default)
        } else {
            Log.d(TAG, "No saved instance state, loading default image.")
            loadDefaultAndSplitImage() // Fresh start, load default
        }

        initializeBoard(savedInstanceState == null) // Pass flag to skip shuffle if restoring

        buttonShuffle.setOnClickListener {
            Log.d(TAG, "Shuffle / New Game button clicked")
            isGameWon = false
            textViewStatus.text = ""
            gameStarted = false
            stopTimer()
            resetTimerDisplay()
            resetMoveCounter()
            // customImageUriString = null // Explicitly clear custom image for "New Game"
            // loadDefaultAndSplitImage() // Load default for "New Game"
            if (fullPuzzleImage == null || imagePieces.isEmpty()) {
                Log.w(TAG, "No image loaded for shuffle, attempting to load default.")
                loadDefaultAndSplitImage() // Fallback
            }
            shuffleBoard()
        }

        buttonToggleMode.setOnClickListener {
            currentDisplayMode = when (currentDisplayMode) {
                DisplayMode.NUMBERS_ONLY -> DisplayMode.IMAGE_ONLY
                DisplayMode.IMAGE_ONLY -> DisplayMode.NUMBERS_AND_IMAGE
                DisplayMode.NUMBERS_AND_IMAGE -> DisplayMode.NUMBERS_ONLY
            }
            updateDisplayModeButtonText()
            updateBoardUI() // Uses current fullPuzzleImage
        }

        buttonToggleDifficulty.setOnClickListener {
            currentDifficulty = when (currentDifficulty) {
                Difficulty.EASY -> Difficulty.MEDIUM
                Difficulty.MEDIUM -> Difficulty.HARD
                Difficulty.HARD -> Difficulty.EASY
            }
            updateDifficultyButtonText()
            Log.d(TAG, "Difficulty changed to: $currentDifficulty. Reshuffling.")
            shuffleBoard() // Reshuffles with the current image (custom or default)
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
                timerHandler.post(timerRunnable) // Re-post if it was running
            } else {
                updateTimerDisplayFromSavedValues() // Update display if timer had a value but wasn't running
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
        if (isTimerRunning) {
            outState.putLong(KEY_CURRENT_ELAPSED_TIME, System.currentTimeMillis() - startTime)
        }
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
            timeInMilliseconds = savedInstanceState.getLong(KEY_CURRENT_ELAPSED_TIME, 0L)
        } else {
            timeInMilliseconds = 0L
        }
        customImageUriString = savedInstanceState.getString(KEY_CUSTOM_IMAGE_URI)
        blankPos = tiles.indexOf(0) // Important to update blankPos from restored tiles
    }

    private fun loadImageBasedOnState() {
        if (customImageUriString != null) {
            try {
                Log.d(TAG, "Attempting to load custom image from URI: $customImageUriString")
                val uri = Uri.parse(customImageUriString)
                val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                if (bitmap != null) {
                    fullPuzzleImage = bitmap
                    splitImagePiecesFromBitmap(fullPuzzleImage)
                    Log.d(TAG, "Successfully loaded custom image from URI for restore/init.")
                    return // Custom image loaded, exit
                } else {
                    Log.e(TAG, "Bitmap from custom URI was null during restore/init.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading custom image from URI during restore/init: $e. Falling back to default.")
                customImageUriString = null // Invalidate bad URI
            }
        }
        // If customImageUriString is null or loading failed, load default
        Log.d(TAG, "Loading default image during restore/init.")
        loadDefaultAndSplitImage()
    }

    private fun updateTimerDisplayFromSavedValues() {
        updateTime = timeSwapBuff
        val secs = (updateTime / 1000).toInt()
        val mins = secs / 60
        val displaySecs = secs % 60
        textViewTimer.text = getString(R.string.timer_format, mins, displaySecs)
    }

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

    private fun setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                Log.d(TAG, "Image picked: $it")
                val cropOptions = CropImageContractOptions(
                    uri = it,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON,
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
                    customImageUriString = croppedUri.toString() // Save the URI string
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, croppedUri)
                        if (bitmap != null) {
                            fullPuzzleImage = bitmap
                            splitImagePiecesFromBitmap(fullPuzzleImage)
                            currentDisplayMode = DisplayMode.IMAGE_ONLY // Switch to image mode
                            updateDisplayModeButtonText()
                            shuffleBoard() // Reshuffle with the new image
                            Toast.makeText(this, "Custom image loaded!", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e(TAG, "Failed to decode cropped bitmap.")
                            Toast.makeText(this, "Failed to load cropped image.", Toast.LENGTH_SHORT).show()
                            customImageUriString = null // Clear if loading failed
                            loadDefaultAndSplitImage() // Fallback to default
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error loading cropped image: ${e.message}", e)
                        Toast.makeText(this, "Error loading image.", Toast.LENGTH_SHORT).show()
                        customImageUriString = null // Clear if loading failed
                        loadDefaultAndSplitImage() // Fallback to default
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
            // customImageUriString is NOT cleared here. It's only cleared by "New Game" or failed custom load.
            Log.d(TAG, "Default image loaded and split.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default image: ${e.message}")
            fullPuzzleImage = null
        }
    }

    private fun splitImagePiecesFromBitmap(sourceBitmap: Bitmap?) {
        imagePieces.clear()
        sourceBitmap?.let { bmp ->
            val pieceWidth = bmp.width / gridSize
            val pieceHeight = bmp.height / gridSize
            if (pieceWidth <= 0 || pieceHeight <= 0) { // Check for non-positive dimensions
                Log.e(TAG, "Image piece dimensions are zero or negative for splitting. Image might be too small or not loaded.")
                fullPuzzleImage = null // Invalidate if pieces can't be made
                imagePieces.clear() // Ensure imagePieces is empty if splitting fails
                return
            }
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    try {
                        val piece = Bitmap.createBitmap(bmp, col * pieceWidth, row * pieceHeight, pieceWidth, pieceHeight)
                        imagePieces.add(BitmapDrawable(resources, piece))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating bitmap piece at $row, $col: ${e.message}")
                        // Consider adding a placeholder or stopping if a piece fails
                    }
                }
            }
            Log.d(TAG, "Bitmap split into ${imagePieces.size} pieces.")
            if (imagePieces.size != gridSize * gridSize) {
                Log.e(TAG, "Error: Expected ${gridSize*gridSize} pieces, but got ${imagePieces.size}. Clearing pieces.")
                imagePieces.clear() // Clear if not all pieces were created
                fullPuzzleImage = null // Also invalidate full image if splitting failed
            }
        } ?: run {
            Log.e(TAG, "Source bitmap for splitting is null.")
            imagePieces.clear() // Ensure imagePieces is empty
        }
    }

    private val timerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isTimerRunning) return // Safety check

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
            startTime = System.currentTimeMillis() // Set startTime relative to current time
            // timeSwapBuff already holds any previously accumulated time
            timerHandler.post(timerRunnable)
            isTimerRunning = true
            Log.d(TAG, "Timer started. startTime: $startTime, timeSwapBuff: $timeSwapBuff")
        }
    }

    private fun stopTimer() {
        if (isTimerRunning) {
            timeSwapBuff += (System.currentTimeMillis() - startTime) // Add current segment to buffer
            timerHandler.removeCallbacks(timerRunnable)
            isTimerRunning = false
            Log.d(TAG, "Timer stopped. timeSwapBuff: $timeSwapBuff")
        }
    }

    private fun resetTimerDisplay() {
        timeSwapBuff = 0L
        timeInMilliseconds = 0L
        updateTime = 0L
        startTime = 0L // Reset startTime
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

        if (performShuffle) {
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
                // If restoring, tiles array is already set from restoreState
                updateBoardUI()
                if (isGameWon) {
                    textViewStatus.text = getString(R.string.win_message)
                }
            }
        }
        Log.d(TAG, "initializeBoard finished (post block scheduled)")
    }

    private fun updateBoardUI() {
        Log.d(TAG, "updateBoardUI started. DisplayMode: $currentDisplayMode, GameWon: $isGameWon, CustomURI: $customImageUriString")
        if (buttons.isEmpty() || buttons.size != gridSize * gridSize) {
            Log.e(TAG, "updateBoardUI: Buttons not initialized or wrong count. Aborting.")
            return
        }
        // imageAvailable check now relies on fullPuzzleImage and correctly sized imagePieces
        val imageAvailable = fullPuzzleImage != null && imagePieces.size == gridSize * gridSize

        val outlineColor = ContextCompat.getColor(this, R.color.tile_outline_color)

        for (i in 0 until gridSize * gridSize) {
            if (i >= buttons.size || i >= tiles.size) { // Safety check
                Log.e(TAG, "Index out of bounds in updateBoardUI loop: i=$i")
                continue
            }
            val button = buttons[i]
            val tileValue = tiles[i]

            button.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

            if (isGameWon && i == gridSize * gridSize - 1) { // Last piece when game is won
                button.isClickable = false
                button.setTextColor(ContextCompat.getColor(this, R.color.tile_text_color))
                val pieceIndexForWonGame = gridSize * gridSize - 1 // The 16th piece
                when (currentDisplayMode) {
                    DisplayMode.NUMBERS_ONLY -> {
                        button.text = (pieceIndexForWonGame + 1).toString() // "16"
                        button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                    }
                    DisplayMode.IMAGE_ONLY -> {
                        button.text = ""
                        if (imageAvailable && imagePieces.indices.contains(pieceIndexForWonGame)) {
                             button.background = imagePieces[pieceIndexForWonGame]
                        } else {
                            button.text = (pieceIndexForWonGame + 1).toString()
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                    DisplayMode.NUMBERS_AND_IMAGE -> {
                        button.text = (pieceIndexForWonGame + 1).toString()
                        if (imageAvailable && imagePieces.indices.contains(pieceIndexForWonGame)) {
                            button.background = imagePieces[pieceIndexForWonGame]
                            button.setShadowLayer(2f, 1f, 1f, outlineColor)
                        } else {
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                }
            } else if (tileValue == 0) { // Blank tile (game not won)
                button.text = ""
                button.background = ContextCompat.getDrawable(this, R.drawable.blank_tile_background)
                button.isClickable = false
                blankPos = i
            } else { // Normal numbered tile
                button.isClickable = !isGameWon
                button.setTextColor(ContextCompat.getColor(this, R.color.tile_text_color))
                val pieceIndex = tileValue - 1 // For 1-15 pieces
                when (currentDisplayMode) {
                    DisplayMode.NUMBERS_ONLY -> {
                        button.text = tileValue.toString()
                        button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                    }
                    DisplayMode.IMAGE_ONLY -> {
                        button.text = ""
                        if (imageAvailable && imagePieces.indices.contains(pieceIndex)) {
                            button.background = imagePieces[pieceIndex]
                        } else {
                            button.text = tileValue.toString()
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                    DisplayMode.NUMBERS_AND_IMAGE -> {
                        button.text = tileValue.toString()
                        if (imageAvailable && imagePieces.indices.contains(pieceIndex)) {
                            button.background = imagePieces[pieceIndex]
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
        if (clickedPhysicalPos < 0 || clickedPhysicalPos >= tiles.size) { // Safety check
            Log.e(TAG, "Invalid clickedPhysicalPos: $clickedPhysicalPos")
            return
        }
        Log.d(TAG, "onTileClick: clickedPhysicalPos=$clickedPhysicalPos")

        if (!gameStarted) {
            gameStarted = true
            startTimer()
        }

        val blankPhysicalPos = tiles.indexOf(0)
        if (blankPhysicalPos == -1) { // Safety check if blank tile isn't found
            Log.e(TAG, "Blank tile (0) not found in tiles array!")
            return
        }

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
        isGameWon = false

        // The image (custom or default) should already be in fullPuzzleImage
        // and imagePieces should be populated by loadImageBasedOnState or loadDefaultAndSplitImage.
        // If fullPuzzleImage is null here, it's an issue.
        if (fullPuzzleImage == null || imagePieces.size != gridSize * gridSize) {
            Log.e(TAG, "Image not ready for shuffleBoard. Attempting to load default.")
            loadDefaultAndSplitImage() // Try to recover by loading default
            if (fullPuzzleImage == null || imagePieces.size != gridSize * gridSize) {
                Log.e(TAG, "Still no valid image after trying default. Cannot shuffle properly.")
                Toast.makeText(this, "Error: Image not loaded for puzzle.", Toast.LENGTH_LONG).show()
                return // Cannot proceed without image pieces
            }
        }


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

        resetMoveCounter()
        if (gameStarted || isTimerRunning) {
            stopTimer()
            resetTimerDisplay()
            gameStarted = false
        }
        textViewStatus.text = ""
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
            updateBoardUI() // Redraw to show 16th piece and disable clicks
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