import 'package:flutter/material.dart';
import 'package:workmanager/workmanager.dart';
import 'utils/app_theme.dart';
import 'services/auth_service.dart';
import 'services/api_service.dart';
import 'services/connectivity_service.dart';
import 'services/notification_service.dart';
import 'screens/login/login_screen.dart';
import 'screens/dashboard/dashboard_screen.dart';

// Callback para tareas en background
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    try {
      // Inicializar servicios necesarios
      final apiService = ApiService();
      await apiService.loadBaseUrl();
      
      final connectivityService = ConnectivityService();
      connectivityService.initialize();
      
      final notificationService = NotificationService();
      await notificationService.initialize();
      
      // Procesar cola
      final syncedCount = await connectivityService.processQueueInBackground();
      
      if (syncedCount > 0) {
        await notificationService.showSyncComplete(syncedCount);
      }
      
      return Future.value(true);
    } catch (e) {
      return Future.value(false);
    }
  });
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Cargar URL guardada
  final apiService = ApiService();
  await apiService.loadBaseUrl();
  
  // Inicializar notificaciones
  final notificationService = NotificationService();
  await notificationService.initialize();
  
  // Inicializar WorkManager
  await Workmanager().initialize(
    callbackDispatcher,
    isInDebugMode: false,
  );
  
  // Registrar tarea periódica (cada 15 minutos)
  await Workmanager().registerPeriodicTask(
    "sync-offline-queue",
    "syncOfflineQueue",
    frequency: const Duration(minutes: 15),
    constraints: Constraints(
      networkType: NetworkType.connected,
    ),
  );
  
  // Inicializar servicio de conectividad
  final connectivityService = ConnectivityService();
  connectivityService.initialize();
  
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ILAC Interflon',
      theme: AppTheme.darkTheme,
      debugShowCheckedModeBanner: false,
      home: const AuthWrapper(),
    );
  }
}

class AuthWrapper extends StatefulWidget {
  const AuthWrapper({super.key});

  @override
  State<AuthWrapper> createState() => _AuthWrapperState();
}

class _AuthWrapperState extends State<AuthWrapper> {
  final AuthService _authService = AuthService();
  bool _isLoading = true;
  bool _isLoggedIn = false;

  @override
  void initState() {
    super.initState();
    _checkAuth();
  }

  Future<void> _checkAuth() async {
    final isLoggedIn = await _authService.tryAutoLogin();
    setState(() {
      _isLoggedIn = isLoggedIn;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(
            color: AppTheme.primaryRed,
          ),
        ),
      );
    }

    if (_isLoggedIn) {
      return const DashboardScreen();
    } else {
      return const LoginScreen();
    }
  }
}
