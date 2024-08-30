package com.nishith.mediapicker.data.di.module

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.nishith.mediapicker.base.BaseActivity
import com.nishith.mediapicker.fileselector.MediaSelectHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    @Provides
    @ActivityScoped
    internal fun provideBaseActivity(@ActivityContext context: Context): BaseActivity {
        return (context as BaseActivity)
    }

    @Provides
    @ActivityScoped
    internal fun provideImagePicker(baseActivity: BaseActivity): MediaSelectHelper {
        return MediaSelectHelper(baseActivity)
    }
}