import androidx.compose.material3.Text

@Composable
fun SettingsPage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Text("Settings Screen")
    }
}