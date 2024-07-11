package fr.theskyblockman.lifechest.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.ui.theme.AppTheme
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnboardingActivityContent(finish = { finish() })
        }
    }
}

@Preview(name = "OnboardingActivity")
@Composable
fun OnboardingActivityPreview() {
    OnboardingActivityContent {

    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingActivityContent(finish: () -> Unit) {
    AppTheme {
        val pagerState = rememberPagerState(pageCount = {
            3
        })
        val isLastPage = pagerState.currentPage == 2

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    actions = {
                        val alpha: Float by animateFloatAsState(
                            if (isLastPage) 0f else 1f,
                            label = "Fading in/out buttons"
                        )
                        Button(
                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp), onClick =
                            {
                                finish()
                            }, modifier = Modifier.alpha(alpha)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_double_arrow_24),
                                contentDescription = stringResource(id = R.string.skip),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = stringResource(id = R.string.skip))
                        }
                    }
                )
            },
            floatingActionButton = {
                val alpha: Float by animateFloatAsState(
                    if (isLastPage) 1f else 0f,
                    label = "Fading in/out buttons"
                )
                val buttonScope = rememberCoroutineScope()

                if (alpha != 1f) {
                    Button(
                        onClick =
                        {
                            buttonScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }, modifier = Modifier.alpha(1 - alpha)
                    ) {
                        Text(text = stringResource(id = R.string.next))
                    }
                }

                if (alpha != 0f) {
                    Button(
                        contentPadding = PaddingValues(start = 16.dp, end = 24.dp), onClick =
                        {
                            buttonScope.launch {
                                finish()
                            }
                        }, modifier = Modifier.alpha(alpha)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = stringResource(id = R.string.start_using_the_app),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = stringResource(id = R.string.start_using_the_app))
                    }
                }

            },
            floatingActionButtonPosition = FabPosition.Center
        ) { innerPadding ->
            HorizontalPager(state = pagerState) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    Arrangement.SpaceEvenly,
                    Alignment.CenterHorizontally
                ) {
                    when (page) {
                        0 -> PageOne()
                        1 -> PageTwo()
                        2 -> PageThree()
                        else -> {
                            Text(
                                text = "Page $page",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun PageOne() {
    Text(
        text = stringResource(id = R.string.welcome_page_1_title),
        style = MaterialTheme.typography.displayMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.ExtraBold
    )
    Text(
        text = stringResource(id = R.string.welcome_page_1_content),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.W500
    )
}

@Composable
fun PageTwo() {
    Text(
        text = stringResource(id = R.string.welcome_page_2_title),
        style = MaterialTheme.typography.displayMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.ExtraBold
    )
    Text(
        text = stringResource(id = R.string.welcome_page_2_content),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.W500
    )
}

@Composable
fun PageThree() {
    Text(
        text = stringResource(id = R.string.welcome_page_3_title),
        style = MaterialTheme.typography.displayMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.ExtraBold
    )
    Text(
        text = stringResource(id = R.string.welcome_page_3_content),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.W500
    )
}