package us.beary.netlens.core.oui;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class OuiLookup_Factory implements Factory<OuiLookup> {
  private final Provider<Context> contextProvider;

  public OuiLookup_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public OuiLookup get() {
    return newInstance(contextProvider.get());
  }

  public static OuiLookup_Factory create(Provider<Context> contextProvider) {
    return new OuiLookup_Factory(contextProvider);
  }

  public static OuiLookup newInstance(Context context) {
    return new OuiLookup(context);
  }
}
