package us.beary.netlens.core.data.di;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import us.beary.netlens.core.data.NetLensDatabase;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class DataModule_ProvideDatabaseFactory implements Factory<NetLensDatabase> {
  private final Provider<Context> contextProvider;

  public DataModule_ProvideDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public NetLensDatabase get() {
    return provideDatabase(contextProvider.get());
  }

  public static DataModule_ProvideDatabaseFactory create(Provider<Context> contextProvider) {
    return new DataModule_ProvideDatabaseFactory(contextProvider);
  }

  public static NetLensDatabase provideDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideDatabase(context));
  }
}
