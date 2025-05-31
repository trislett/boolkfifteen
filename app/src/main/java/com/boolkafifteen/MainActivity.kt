package com.boolkafifteen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import kotlin.random.Random

enum class DisplayMode { NUMBERS_ONLY, IMAGE_ONLY, NUMBERS_AND_IMAGE }
// Updated Difficulty Enum
enum class Difficulty { EASY, MEDIUM, HARD }

class MainActivity : AppCompatActivity() {

    // ... (other variable declarations remain the same) ...
    private lateinit var gridLayoutBoard: GridLayout
    private lateinit var buttonShuffle: Button
    private lateinit var textViewStatus: TextView
    private lateinit var textViewMoves: TextView
    private lateinit var textViewTimer: TextView
    private lateinit var buttonToggleMode: Button
    private lateinit var buttonToggleDifficulty: Button

    private val gridSize = 4
    private var tiles = IntArray(gridSize * gridSize)
    private var buttons = mutableListOf<Button>()
    private var blankPos = gridSize * gridSize - 1
    private var moveCount = 0

    private var currentDisplayMode = DisplayMode.NUMBERS_ONLY
    private var currentDifficulty = Difficulty.HARD // Default to hard
    private var isGameWon = false

    private var imagePieces = mutableListOf<BitmapDrawable?>()
    private var fullPuzzleImage: Bitmap? = null

    private val timerHandler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var timeInMilliseconds = 0L
    private var timeSwapBuff = 0L
    private var updateTime = 0L
    private var isTimerRunning = false
    private var gameStarted = false


    companion object {
        private const val TAG = "BoolkaFifteenGame"
        // Shuffle counts for different difficulties
        private const val EASY_SHUFFLE_MOVES = 100
        private const val MEDIUM_SHUFFLE_MOVES = 500
        private const val HARD_SHUFFLE_MOVES = 5000
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

        Log.d(TAG, "Views initialized by ID")

        loadAndSplitImage()
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

        updateDisplayModeButtonText()
        updateDifficultyButtonText()
        updateMovesDisplay()
        resetTimerDisplay()
        Log.d(TAG, "onCreate finished")
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

    // ... (loadAndSplitImage, timer logic, move counter logic remain the same) ...
    private fun loadAndSplitImage() {
        try {
            val inputStream = resources.openRawResource(R.drawable.my_puzzle_image)
            fullPuzzleImage = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            fullPuzzleImage?.let { bmp ->
                imagePieces.clear()
                val pieceWidth = bmp.width / gridSize
                val pieceHeight = bmp.height / gridSize
                if (pieceWidth == 0 || pieceHeight == 0) {
                    Log.e(TAG, "Image piece dimensions are zero.")
                    fullPuzzleImage = null
                    return
                }
                for (row in 0 until gridSize) {
                    for (col in 0 until gridSize) {
                        val piece = Bitmap.createBitmap(bmp, col * pieceWidth, row * pieceHeight, pieceWidth, pieceHeight)
                        imagePieces.add(BitmapDrawable(resources, piece))
                    }
                }
                Log.d(TAG, "Image loaded and split into ${imagePieces.size} pieces.")
            } ?: Log.e(TAG, "Failed to load full puzzle image.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading or splitting image: ${e.message}")
            fullPuzzleImage = null
        }
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
        // Use the new outline color
        val outlineColor = ContextCompat.getColor(this, R.color.tile_outline_color)

        for (i in 0 until gridSize * gridSize) {
            val button = buttons[i]
            val tileValue = tiles[i]

            button.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT) // Reset shadow

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
                        if (imageAvailable) button.background = imagePieces[gridSize * gridSize - 1]
                        else {
                            button.text = "16"
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                    DisplayMode.NUMBERS_AND_IMAGE -> {
                        button.text = "16"
                        if (imageAvailable) {
                            button.background = imagePieces[gridSize * gridSize - 1]
                            button.setShadowLayer(2f, 1f, 1f, outlineColor) // Use new outline color
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
                        if (imageAvailable) button.background = imagePieces[tileValue - 1]
                        else {
                            button.text = tileValue.toString()
                            button.background = ContextCompat.getDrawable(this, R.drawable.numbered_tile_background)
                        }
                    }
                    DisplayMode.NUMBERS_AND_IMAGE -> {
                        button.text = tileValue.toString()
                        if (imageAvailable) {
                            button.background = imagePieces[tileValue - 1]
                            button.setShadowLayer(2f, 1f, 1f, outlineColor) // Use new outline color
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

    // MODIFIED shuffleBoard for difficulty levels based on move count
    private fun shuffleBoard() {
        Log.d(TAG, "shuffleBoard started - Difficulty: $currentDifficulty")
        isGameWon = false
        
        // Always start from a solved state
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
        blankPos = tiles.indexOf(0) // Ensure blankPos is correctly set after shuffling

        resetMoveCounter()
        if (gameStarted || isTimerRunning) {
            stopTimer()
            resetTimerDisplay()
            gameStarted = false
        }
        updateBoardUI()
    }

    // isSolvable is no longer needed if we always shuffle from a solved state
    /*
    private fun isSolvable(currentTiles: IntArray): Boolean {
        // ... (implementation can be removed or commented out) ...
    }
    */

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
