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
    private var defaultPuzzleImageLoaded = false

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate started")

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

        loadDefaultAndSplitImage()
        initializeBoard()

        buttonShuffle.setOnClickListener {
            Log.d(TAG, "Shuffle button clicked")
            isGameWon = false
            textViewStatus.text = ""
            gameStarted = false
            stopTimer()
            resetTimerDisplay()
            resetMoveCounter()
            shuffleBoard()
        }

        buttonToggleMode.setOnClickListener {
            currentDisplayMode = when (currentDisplayMode) {
                DisplayMode.NUMBERS_ONLY -> DisplayMode.IMAGE_ONLY
                DisplayMode.IMAGE_ONLY -> DisplayMode.NUMBERS_AND_IMAGE
                DisplayMode.NUMBERS_AND_IMAGE -> DisplayMode.NUMBERS_ONLY
            }
            updateDisplayModeButtonText() // DEFINED BELOW
            updateBoardUI()
        }

        buttonToggleDifficulty.setOnClickListener {
            currentDifficulty = when (currentDifficulty) {
                Difficulty.EASY -> Difficulty.MEDIUM
                Difficulty.MEDIUM -> Difficulty.HARD
                Difficulty.HARD -> Difficulty.EASY
            }
            updateDifficultyButtonText() // DEFINED BELOW
            Log.d(TAG, "Difficulty changed to: $currentDifficulty")
        }

        buttonLoadCustomImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        updateDisplayModeButtonText() // DEFINED BELOW
        updateDifficultyButtonText() // DEFINED BELOW
        updateMovesDisplay()
        resetTimerDisplay()
        Log.d(TAG, "onCreate finished")
    }

    // --- DEFINITIONS FOR THE MISSING FUNCTIONS ---
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
    // --- END OF DEFINITIONS FOR MISSING FUNCTIONS ---


    private fun setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                Log.d(TAG, "Image picked: $it")
                val cropOptions = CropImageContractOptions(
                    uri = it,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON, // Uses the imported CropImageView
                        aspectRatioX = 1,
                        aspectRatioY = 1,
                        fixAspectRatio = true,
                        outputCompressQuality = 80
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
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, croppedUri)
                        if (bitmap != null) {
                            fullPuzzleImage = bitmap
                            splitImagePiecesFromBitmap(fullPuzzleImage)
                            defaultPuzzleImageLoaded = false
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
            textViewTimer.text = getString(R.string.timer_format, mins, displaySecs) // Uses corrected format
            timerHandler.postDelayed(this, 500)
        }
    }

    private fun startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis()
            timerHandler.post(timerRunnable)
            isTimerRunning = true
            Log.d(TAG, "Timer started.")
        }
    }

    private fun stopTimer() {
        if (isTimerRunning) {
            timeSwapBuff += timeInMilliseconds
            timerHandler.removeCallbacks(timerRunnable)
            isTimerRunning = false
            Log.d(TAG, "Timer stopped.")
        }
    }

    private fun resetTimerDisplay() {
        timeSwapBuff = 0L
        timeInMilliseconds = 0L
        updateTime = 0L
        textViewTimer.text = getString(R.string.timer_format, 0, 0) // Uses corrected format
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

    private fun initializeBoard() {
        Log.d(TAG, "initializeBoard started")
        isGameWon = false
        buttons.clear()
        gridLayoutBoard.removeAllViews()
        resetMoveCounter()
        resetTimerDisplay()
        gameStarted = false

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
            shuffleBoard()
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
        isGameWon = false

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
