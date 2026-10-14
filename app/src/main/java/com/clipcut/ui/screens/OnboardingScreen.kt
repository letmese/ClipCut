package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.ui.components.PrimaryButton
import com.clipcut.ui.components.SecondaryButton
import com.clipcut.ui.theme.AccentPrimary
import com.clipcut.ui.theme.DarkBackground
import kotlinx.coroutines.launch

/**
 * OnboardingScreen — 3-slide introduction shown on first launch only.
 * MainActivity routes here when SharedPreferences does not have
 * "onboarding_complete". On finishing, MainActivity writes the pref and
 * navigates to Screen.ProjectList.
 *
 * Slides:
 *   1. Welcome — "A professional video editor in your pocket"
 *   2. Tools  — "22 editing tools, keyframes, multi-track timeline"
 *   3. Effects — "40+ effects, 25+ transitions, 30+ filters"
 *
 * Each slide has: full-bleed gradient, large icon, title, description.
 * Bottom: Skip button (left) + Next/Get Started button (right) + page dots.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text("ClipCut", color = AccentPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                OnboardingSlide(page)
            }

            // Page indicator dots
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { i ->
                    val selected = pager.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) AccentPrimary else Color.White.copy(alpha = 0.3f))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton("Skip", onClick = onDone)
                if (pager.currentPage < 2) {
                    PrimaryButton("Next", onClick = {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    })
                } else {
                    PrimaryButton("Get Started", onClick = onDone)
                }
            }
        }
    }
}

@Composable
private fun OnboardingSlide(page: Int) {
    val (title, desc, icon, gradient) = when (page) {
        0 -> Quad("Welcome to ClipCut",
            "A professional video editor in your pocket.",
            Icons.Filled.Movie, listOf(AccentPrimary, AccentPrimary.copy(alpha = 0.4f)))
        1 -> Quad("Powerful Tools",
            "22 editing tools, keyframes, multi-track timeline.",
            Icons.Filled.Build, listOf(Color(0xFF06D6A0), Color(0xFF06D6A0).copy(alpha = 0.4f)))
        else -> Quad("Cinematic Effects",
            "40+ effects, 25+ transitions, 30+ filters.",
            Icons.Filled.AutoAwesome, listOf(Color(0xFF9B5DE5), Color(0xFF9B5DE5).copy(alpha = 0.4f)))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(desc, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

private data class Quad(val title: String, val desc: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val gradient: List<Color>)
