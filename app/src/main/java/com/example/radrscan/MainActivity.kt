package com.example.radrscan

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.content.ContentValues.TAG
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberImagePainter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.radrscan.ui.theme.RadRScanTheme
import com.google.ai.client.generativeai.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Objects

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RadRScanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting()
                }
            }
        }
    }
}

@Composable
fun Greeting() {

    val db= Firebase.firestore
    val context = LocalContext.current
    val apikey= com.example.radrscan.BuildConfig.apikey
    val Gemini=GenerativeModel(
        modelName = "gemini-1.5-flash", apiKey =apikey
    )

    val file = context.createImageFile()
    val uri = FileProvider.getUriForFile(
        Objects.requireNonNull(context),
        context.packageName + ".provider", file
    )
    Log.d("uri",uri.toString())
    var capturedImageUri by remember {
        mutableStateOf<Uri>(Uri.EMPTY)
    }
    var productname by remember {
        mutableStateOf("")
    }
    var Colour by remember {
        mutableStateOf("")
    }
    var Pattern by remember {
        mutableStateOf("")
    }
    var Description by remember {
        mutableStateOf("")
    }
    var loading by remember {
        mutableStateOf(false)
    }



    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){
            capturedImageUri = uri
        }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){
        if (it)
        {
            Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT).show()
            cameraLauncher.launch(uri)
        }
        else
        {
            Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }




    Column(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
            .scrollable(
                enabled = true,
                orientation = Orientation.Vertical,
                state = rememberScrollState()
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (loading){
            Column(modifier = Modifier
                .fillMaxSize()) {
                val composition by rememberLottieComposition(
                    spec = LottieCompositionSpec.RawRes(R.raw.loading_animatio2))

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column {
                        LottieAnimation(composition = composition, iterations = LottieConstants.IterateForever)
                        Text(text = "Fetching Details....", color = Color.Blue, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }


                }

            }

        }
        if (capturedImageUri.path?.isNotEmpty() == true)
        {
            val bitmapimage= getBitmapFromUri(context,capturedImageUri)
            var toast=""
            LaunchedEffect (bitmapimage != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    if (toast==""){
                        loading=true
                    }
                    toast=run(context,Gemini,bitmapimage!!)
                    withContext(Dispatchers.Main) {
                        if (toast!=""){
                            loading=false
                        }
                        Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
                        val trimmedJsonString = toast.trim().removePrefix("```json").removeSuffix("```")
                        val jsonObject = JSONObject(trimmedJsonString)
                        val productName = jsonObject.getString("Product Name")
                        val description = jsonObject.getString("Description")
                        val colour = jsonObject.getString("Colour")
                        val pattern = jsonObject.getString("Pattern")
                        productname=productName
                        Description=description
                        Colour=colour
                        Pattern=pattern

                        val product = hashMapOf(
                            "Product Name" to productName,
                            "Description" to description,
                            "Colour" to colour,
                            "Pattern" to pattern)
                        db.collection("products")
                            .add(product)
                            .addOnSuccessListener { documentReference ->
                                Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Error adding product", e)
                            }
                    }
                }

            }

            Image(
                modifier = Modifier
                    .height(400.dp)
                    .width(400.dp)
                    .padding(16.dp, 8.dp)
                    .padding(bottom = 50.dp),
                painter = rememberImagePainter(capturedImageUri),
                contentDescription = null
            )
        }
        else
        {
            Image(
                modifier = Modifier
                    .height(300.dp)
                    .width(300.dp)
                    .padding(16.dp, 8.dp)
                    .padding(bottom = 50.dp),
                painter = painterResource(id = R.drawable.ic_image),
                contentDescription = null
            )
        }

        OutlinedTextField(readOnly = true, value = productname, onValueChange = {productname=it}, label = { Text(text = "Name")})
        OutlinedTextField(readOnly = true, value = Description, onValueChange = {Description=it}, label = { Text(text = "Description")})
        OutlinedTextField(readOnly = true, value = Pattern, onValueChange = {Pattern=it}, label = { Text(text = "Pattern")})
        OutlinedTextField(readOnly = true, value = Colour, onValueChange = {Colour=it}, label = { Text(text = "Colour")})



        FloatingActionButton(modifier = Modifier
            .align(Alignment.End)
            .padding(10.dp),onClick = {
            val permissionCheckResult =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)

            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED)
            {
                cameraLauncher.launch(uri)
            }
            else
            {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }) {
            Text(text = "Capture Image")
        }

    }


}

fun Context.createImageFile(): File {
    val timeStamp = SimpleDateFormat("yyyy_MM_dd_HH:mm:ss").format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    Log.d("cache",externalCacheDir.toString())
    val image = File.createTempFile(
        imageFileName,
        ".jpg",
        externalCacheDir
    )


    return image
}
fun getBitmapFromUri(context: Context, imageUri: Uri): Bitmap? {
    try {
        val inputStream = context.contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        return bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
suspend fun run (context: Context, gemini: GenerativeModel, image: Bitmap):String{
    val inputContent = content {

        image(image)
        text("you are a online store owner and you are looking at images of different products to add to your online store.\n" +
                "tell me what you see in json format.The JSON file should contain the following fields:\n" +
                "Product Name\n" +
                "Description\n" +
                "Colour\n" +
                "Pattern\n" +
                "only one product at centre"
        )
    }
    val response=gemini.generateContent(inputContent)
    Log.d("jfknckfsndf",response.text.toString())
    return response.text.toString()

}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RadRScanTheme {
        Greeting()
    }
}

