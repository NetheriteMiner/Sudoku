import Difficulty.Companion.display
import Difficulty.Companion.req
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.pow

var boardNeedsReset = true
var origBoard = ""
var ansBoard = ""
var difficulty = Difficulty.EASY

fun hasConflict(board: SnapshotStateList<Array<MutableState<Int>>>, position: Pair<Int, Int>): Boolean {
    val number = board[position.first][position.second].value
    if (number == 0) return false
    for (i in 0..8) {
        if (i == position.second) continue
        if (board[position.first][i].value == number) return true
    }
    for (i in 0..8) {
        if (i == position.first) continue
        if (board[i][position.second].value == number) return true
    }

    val boxNum = (position.second / 3) + (position.first / 3) * 3
    val startX = 3 * (boxNum % 3)
    val startY = boxNum - (boxNum % 3)
    for (i in startY..startY + 2) {
        for (j in startX..startX + 2) {
            if (i == position.first && j == position.second) continue
            if (board[i][j].value == number) return true
        }
    }
    return false
}

fun removePencils(pencils: SnapshotStateList<Array<MutableState<Int>>>, selectedCell: Pair<Int, Int>, newNum: Int) {
    for (i in 0..8) {
        pencils[i][selectedCell.second].value = pencils[i][selectedCell.second].value and (1 shl (newNum-1)).inv()
    }
    for (j in 0..8) {
        pencils[selectedCell.first][j].value = pencils[selectedCell.first][j].value and (1 shl (newNum-1)).inv()
    }

    val minX = selectedCell.first - (selectedCell.first % 3)
    val minY = selectedCell.second - (selectedCell.second % 3)
    for (i in minX..minX+2) {
        for (j in minY..minY+2) {
            pencils[i][j].value = pencils[i][j].value and (1 shl (newNum-1)).inv()
        }
    }
}

fun SnapshotStateList<Array<MutableState<Int>>>.apiToBoard(puzzle: String) {
    var i = 0
    var j = 0
    for (char in puzzle) {
        this[i][j].value = char.digitToInt()

        j++
        if (j < 9) continue
        j = 0
        i++
    }
}

fun SnapshotStateList<Array<MutableState<Int>>>.hasWon(): Boolean {
    for (i in 0..8) {
        var sum = 0
        for (j in 0..8) {
            if (this[i][j].value == 0) return false
            sum += this[i][j].value
        }
        if (sum != 45) return false
    }

    for (j in 0..8) {
        var sum = 0
        for (i in 0..8) {
            sum += this[i][j].value
        }
        if (sum != 45) return false
    }

    return true
}

fun getConflicts(board: SnapshotStateList<Array<MutableState<Int>>>, position: Pair<Int, Int>): List<Int> {
    val conflicts = mutableListOf<Int>()
    for (i in 0..8) {
        if (board[i][position.second].value != 0 && board[i][position.second].value !in conflicts) {
            conflicts.add(board[i][position.second].value)
        }
    }
    for (j in 0..8) {
        if (board[position.first][j].value != 0 && board[position.first][j].value !in conflicts) {
            conflicts.add(board[position.first][j].value)
        }
    }

    val first = position.first - position.first % 3
    val second = position.second - position.second % 3
    for (i in first..first + 2) {
        for (j in second..second + 2) {
            if (board[i][j].value != 0 && board[i][j].value !in conflicts) {
                conflicts.add(board[i][j].value)
            }
        }
    }
    return conflicts
}

fun writeAutoPencil(board: SnapshotStateList<Array<MutableState<Int>>>, pencils: SnapshotStateList<Array<MutableState<Int>>>, autoPencil: Boolean) {
    if (!autoPencil) {
        for (i in 0..8)
            for (j in 0..8)
                pencils[i][j].value = 0
        return
    }

    for (i in 0..8) {
        for (j in 0..8) {
            if (board[i][j].value != 0) continue
            pencils[i][j].value = 511
            for (num in getConflicts(board, Pair(i, j))) {
                pencils[i][j].value -= 2.0.pow(num - 1).toInt()
            }
        }
    }
}

class ApiResponse {
    val puzzle: String = ""
    val solution: String = ""
}

fun reset(board: SnapshotStateList<Array<MutableState<Int>>>, pencils: SnapshotStateList<Array<MutableState<Int>>>) {
    val client = OkHttpClient()
    val body = JsonObject().apply {
        addProperty("difficulty", difficulty.req())
    }
    val requestBody = body.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url("https://youdosudoku.com/api")
        .post(requestBody)
        .addHeader("Content-Type", "application/json")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            println("Unexpected code $response")
            return@use
        }
        val jsonResponse = response.body?.string()
        val gson = Gson()
        val apiResponse = gson.fromJson(jsonResponse, ApiResponse::class.java)

        origBoard = apiResponse.puzzle
        ansBoard = apiResponse.solution
    }

//    origBoard = "000160500600080014009050003407306000500700040000020930072001390000000005050009420"
//    ansBoard = "284163579635987214719254863427396158593718642168425937872541396941632785356879421"
    for (i in 0..8) {
        for (j in 0..8) {
            pencils[i][j].value = 0
        }
    }
    return board.apiToBoard(origBoard)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SudokuBoard() {
    val board = remember { mutableStateListOf(*Array(9) { Array(9) {mutableStateOf(0) } }) }
    var selectedCell by remember { mutableStateOf(Pair(0, 0)) }
    var win by remember { mutableStateOf(false) }
    var autoPencil by remember { mutableStateOf(false) }
    val pencils = remember { mutableStateListOf(*Array(9) { Array(9) { mutableStateOf(0) } }) }
    if (boardNeedsReset) {
        boardNeedsReset = false
        reset(board, pencils)
    }
    val keyMap = mapOf(
        Key.Backspace to 0,
        Key.Zero to 0,
        Key.One to 1,
        Key.Two to 2,
        Key.Three to 3,
        Key.Four to 4,
        Key.Five to 5,
        Key.Six to 6,
        Key.Seven to 7,
        Key.Eight to 8,
        Key.Nine to 9,
        Key.NumPad0 to 0,
        Key.NumPad1 to 1,
        Key.NumPad2 to 2,
        Key.NumPad3 to 3,
        Key.NumPad4 to 4,
        Key.NumPad5 to 5,
        Key.NumPad6 to 6,
        Key.NumPad7 to 7,
        Key.NumPad8 to 8,
        Key.NumPad9 to 9,
    )

    Column(
        modifier = Modifier

            .fillMaxSize()
            .padding(16.dp)
            .focusable(false)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when {
                        keyEvent.key == Key.DirectionUp || keyEvent.key == Key.W -> {
                            selectedCell = Pair(selectedCell.first - 1, selectedCell.second)
                            if (selectedCell.first < 0) selectedCell = Pair(8, selectedCell.second)
                            true
                        }

                        keyEvent.key == Key.DirectionDown || keyEvent.key == Key.S -> {
                            selectedCell = Pair(selectedCell.first + 1, selectedCell.second)
                            if (selectedCell.first > 8) selectedCell = Pair(0, selectedCell.second)
                            true
                        }

                        keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.A -> {
                            selectedCell = Pair(selectedCell.first, selectedCell.second - 1)
                            if (selectedCell.second < 0) selectedCell = Pair(selectedCell.first, 8)
                            true
                        }

                        keyEvent.key == Key.DirectionRight || keyEvent.key == Key.D -> {
                            selectedCell = Pair(selectedCell.first, selectedCell.second + 1)
                            if (selectedCell.second > 8) selectedCell = Pair(selectedCell.first, 0)
                            true
                        }

                        keyEvent.key == Key.P -> {
                            autoPencil = !autoPencil
                            writeAutoPencil(board, pencils, autoPencil)
                            true
                        }

                        keyEvent.key == Key.H -> {
                            val hint = ansBoard[selectedCell.second + selectedCell.first * 9].digitToInt()
                            board[selectedCell.first][selectedCell.second].value = hint
                            removePencils(pencils, selectedCell, hint)
                            pencils[selectedCell.first][selectedCell.second].value = 0
                            win = board.hasWon()
                            true
                        }

                        keyEvent.key in keyMap && origBoard[selectedCell.first * 9 + selectedCell.second] == '0'-> {
                            val number = keyMap[keyEvent.key] ?: board[selectedCell.first][selectedCell.second].value
                            if (keyEvent.isShiftPressed) {
                                if (board[selectedCell.first][selectedCell.second].value != 0) true
                                pencils[selectedCell.first][selectedCell.second].value = pencils[selectedCell.first][selectedCell.second].value xor (1 shl (number - 1))
                            }
                            else {
                                board[selectedCell.first][selectedCell.second].value = number
                                removePencils(pencils, selectedCell, number)
                                pencils[selectedCell.first][selectedCell.second].value = 0
                                win = board.hasWon()
                            }
                            true
                        }

                        else -> false
                    }
                }
                else false
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (i in 0 until 9) {
            Row (
                modifier = Modifier
                    .padding(top = if (i % 3 == 0 && i != 0) 8.dp else 0.dp)
                    .focusable(false)
            ) {
                for (j in 0 until 9) {
                    val pos = Pair(i, j)
                    SudokuCell(
                        value = if (pencils[i][j].value == 0) board[i][j].value else pencils[i][j].value,
                        isSelected = selectedCell == pos,
                        onClick = {
                            selectedCell = pos
                        },
                        position = pos,
                        conflict = hasConflict(board, pos),
                        selectedNum = board[selectedCell.first][selectedCell.second].value,
                        isOriginal = origBoard[9*i + j] != '0',
                        isPencil = pencils[i][j].value != 0,
                    )
                }
            }
        }

        if (win) {
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.padding(10.dp)) {

                TextField(
                    readOnly = true,
                    value = difficulty.display(),
                    onValueChange = {},
                    label = { Text("Difficulty") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // List of options
                    val options = mutableListOf<Difficulty>()
                    for (diff in Difficulty.entries) {
                        options.add(diff)
                    }
                    options.forEach { option ->
                        DropdownMenuItem(
                            content = { Text(option.display()) },
                            onClick = {
                                difficulty = option
                                expanded = false
                            }
                        )
                    }
                }
            }
            Button(
                onClick = {
                    boardNeedsReset = true
                    win = false
                    autoPencil = false
                },
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text("Reset")
            }
        }

        Text(
            "Control with WASD or arrow keys\n" +
                    "Enter numbers with the number row or number pad\n" +
                    "Shift + Num = Pencil marks\n" +
                    "P = Toggle auto write pencil marks\n" +
                    "H = Hint (reveals or checks currently selected cell)\n" +
                    "If the keyboard inputs don't work, try clicking on a cell first",
            color = Color.Black,
            fontSize = 15.sp
        )
    }
}

@Composable
fun SudokuCell(value: Int, isSelected: Boolean, onClick: () -> Unit, position: Pair<Int, Int>, conflict: Boolean, selectedNum: Int, isOriginal: Boolean, isPencil: Boolean) {
    val asString = if (value == 0) "" else value.toString()
    val numToAlign = mapOf(
        1 to Alignment.TopStart,
        2 to Alignment.TopCenter,
        3 to Alignment.TopEnd,
        4 to Alignment.CenterStart,
        5 to Alignment.Center,
        6 to Alignment.CenterEnd,
        7 to Alignment.BottomStart,
        8 to Alignment.BottomCenter,
        9 to Alignment.BottomEnd
    )
    Box(
        modifier = Modifier
            .padding(start = if (position.second % 3 == 0 && position.second != 0) 8.dp else 0.dp)
            .size(40.dp)
            .background(if (isSelected) Color.Cyan else if (conflict) Color.hsl(336f, 1f, 0.50f) else if (!isPencil && value == selectedNum && value != 0) Color.LightGray else if (selectedNum != 0 && isPencil && (value and (1 shl selectedNum-1)) != 0) Color.LightGray else Color.Unspecified)
            .border(BorderStroke(1.dp, if (isSelected) Color.Blue else Color.Black), RoundedCornerShape(4.dp))
            .focusable(false)
            .clickable { onClick() }
            .pointerHoverIcon(if (isOriginal) PointerIcon.Default else PointerIcon.Hand)
            .fillMaxHeight(),
    ) {
        if (!isPencil) {
            Text(
                text = asString,
                fontSize = 20.sp,
                color = if (value == selectedNum) Color.Blue else if (isOriginal) Color.Gray else Color.Black,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        else {
            for (i in 1..9) {
                if ((value and (1 shl i-1)) == 0) continue
                Text(
                    text = "$i",
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    color = if (i == selectedNum) Color.Blue else Color.Black,
                    modifier = Modifier
                        .align(numToAlign[i] ?: Alignment.Center)
                )
            }
        }
    }
}

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD;

    companion object {
        fun Difficulty.req(): String {
            return this.name.lowercase()
        }

        fun Difficulty.display(): String {
            return this.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Menu(onStartGame: () -> Unit) {
    MaterialTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text("Sudoku", color = Color.Black, fontSize = 35.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

            Text("by NetheriteMiner\n\n", color = Color.Gray, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.padding(10.dp)
                    .align(Alignment.CenterHorizontally)) {

                TextField(
                    readOnly = true,
                    value = difficulty.display(),
                    onValueChange = {},
                    label = { Text("Difficulty") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // List of options
                    val options = mutableListOf<Difficulty>()
                    for (diff in Difficulty.entries) {
                        options.add(diff)
                    }
                    options.forEach { option ->
                        DropdownMenuItem(
                            content = { Text(option.display()) },
                            onClick = {
                                difficulty = option
                                expanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text("PLAY")
            }
        }
    }
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Menu) }

        when (currentScreen) {
            Screen.Menu -> Menu { currentScreen = Screen.Game }
            Screen.Game -> SudokuBoard()
        }
    }
}

sealed class Screen {
    object Menu : Screen()
    object Game : Screen()
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Sudoku") {
        App()
    }
}

