# Keine speziellen Regeln nötig. Services, Receiver und Activities
# werden über das Manifest automatisch behalten.

# play-review-ktx verweist auf eine Annotation aus den Play Services, die zur Laufzeit nicht nötig ist.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
