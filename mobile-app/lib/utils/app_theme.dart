import 'package:flutter/material.dart';

class AppTheme {
  // Color palette: Black, White, Red
  static const Color primaryRed = Color(0xFFD32F2F);
  static const Color darkRed = Color(0xFFB71C1C);
  static const Color lightRed = Color(0xFFEF5350);
  static const Color black = Color(0xFF212121);
  static const Color darkGrey = Color(0xFF424242);
  static const Color grey = Color(0xFF757575);
  static const Color lightGrey = Color(0xFFBDBDBD);
  static const Color white = Color(0xFFFFFFFF);
  static const Color offWhite = Color(0xFFF5F5F5);

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: ColorScheme.dark(
        primary: primaryRed,
        onPrimary: white,
        primaryContainer: darkRed,
        onPrimaryContainer: white,
        secondary: lightRed,
        onSecondary: white,
        secondaryContainer: darkGrey,
        onSecondaryContainer: white,
        surface: black,
        onSurface: white,
        surfaceContainerHighest: darkGrey,
        onSurfaceVariant: lightGrey,
        error: lightRed,
        onError: white,
      ),
      scaffoldBackgroundColor: black,
      appBarTheme: const AppBarTheme(
        backgroundColor: black,
        foregroundColor: white,
        elevation: 0,
        centerTitle: true,
      ),
      cardTheme: CardThemeData(
        color: darkGrey,
        elevation: 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primaryRed,
          foregroundColor: white,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: primaryRed,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: darkGrey,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: primaryRed),
        ),
        labelStyle: const TextStyle(color: lightGrey),
        hintStyle: const TextStyle(color: grey),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: darkGrey,
        selectedColor: primaryRed,
        labelStyle: const TextStyle(color: white),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: darkGrey,
        thickness: 1,
      ),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(color: white, fontWeight: FontWeight.bold),
        headlineMedium: TextStyle(color: white, fontWeight: FontWeight.bold),
        headlineSmall: TextStyle(color: white, fontWeight: FontWeight.w600),
        titleLarge: TextStyle(color: white, fontWeight: FontWeight.w600),
        titleMedium: TextStyle(color: white),
        titleSmall: TextStyle(color: lightGrey),
        bodyLarge: TextStyle(color: white),
        bodyMedium: TextStyle(color: lightGrey),
        bodySmall: TextStyle(color: grey),
        labelLarge: TextStyle(color: white, fontWeight: FontWeight.w600),
        labelMedium: TextStyle(color: lightGrey),
        labelSmall: TextStyle(color: grey),
      ),
    );
  }
}
