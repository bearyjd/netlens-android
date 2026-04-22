package us.beary.netlens.core.network;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ConnectivityManagerNetworkMonitor_Factory implements Factory<ConnectivityManagerNetworkMonitor> {
  private final Provider<Context> contextProvider;

  public ConnectivityManagerNetworkMonitor_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ConnectivityManagerNetworkMonitor get() {
    return newInstance(contextProvider.get());
  }

  public static ConnectivityManagerNetworkMonitor_Factory create(
      Provider<Context> contextProvider) {
    return new ConnectivityManagerNetworkMonitor_Factory(contextProvider);
  }

  public static ConnectivityManagerNetworkMonitor newInstance(Context context) {
    return new ConnectivityManagerNetworkMonitor(context);
  }
}
