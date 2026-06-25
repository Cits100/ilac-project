import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../services/api_service.dart';
import '../services/database_service.dart';
import '../services/connectivity_service.dart';
import '../models/work_order.dart';

class AuthService {
  static final AuthService _instance = AuthService._internal();
  factory AuthService() => _instance;
  AuthService._internal();

  final ApiService _apiService = ApiService();
  final DatabaseService _dbService = DatabaseService();
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();

  String? _currentIdentity;
  String? _sessionToken;

  String? get currentIdentity => _currentIdentity;
  String? get currentSessionToken => _sessionToken;
  bool get isLoggedIn => _sessionToken != null;

  /// Iniciar sesión y obtener token de sesión
  Future<Map<String, dynamic>> login(String identity, String credential) async {
    final result = await _apiService.login(identity, credential);

    if (result['success'] == true) {
      _currentIdentity = identity;
      _sessionToken = result['sessionToken'];

      // Guardar SOLO en almacenamiento seguro (NO en SQLite)
      await _secureStorage.write(key: 'identity', value: identity);
      await _secureStorage.write(key: 'session_token', value: _sessionToken);

      // Guardar órdenes en base de datos local
      await _dbService.clearWorkOrders();
      
      List<WorkOrder> newOrders = result['newWorkOrders'] ?? [];
      List<WorkOrder> teamOrders = result['teamWorkOrders'] ?? [];
      List<WorkOrder> myOrders = result['myWorkOrders'] ?? [];
      
      await _dbService.insertWorkOrders(newOrders);
      await _dbService.insertWorkOrders(teamOrders);
      await _dbService.insertWorkOrders(myOrders);

      // Procesar cola pendiente después del login
      _processQueueAfterLogin();
    }

    return result;
  }

  /// Intentar auto-login con token guardado
  Future<bool> tryAutoLogin() async {
    try {
      final identity = await _secureStorage.read(key: 'identity');
      final token = await _secureStorage.read(key: 'session_token');

      if (identity == null || token == null) {
        return false;
      }

      _currentIdentity = identity;
      _sessionToken = token;

      // Intentar refrescar datos con el token
      final result = await _apiService.refreshData(token);
      
      if (result['success'] == true) {
        // Token válido, actualizar datos
        _updateLocalData(result);
        return true;
      } else if (result['errorType'] == 'session_expired') {
        // Token expirado, limpiar sesión pero mantener cola
        _sessionToken = null;
        await _secureStorage.delete(key: 'session_token');
        return false;
      }

      // Otro error, intentar usar datos cacheados
      return true;
    } catch (e) {
      // Error de conexión, intentar usar datos cacheados
      final identity = await _secureStorage.read(key: 'identity');
      if (identity != null) {
        _currentIdentity = identity;
        return true;
      }
      return false;
    }
  }

  /// Refrescar datos usando el token actual
  Future<Map<String, dynamic>> refreshData() async {
    if (_sessionToken == null) {
      return {
        'success': false,
        'message': 'No hay sesión activa',
        'errorType': 'session_expired'
      };
    }

    final result = await _apiService.refreshData(_sessionToken!);

    if (result['success'] == true) {
      // Actualizar token si el backend lo renueva
      if (result['sessionToken'] != null) {
        _sessionToken = result['sessionToken'];
        await _secureStorage.write(key: 'session_token', value: _sessionToken);
      }

      // Actualizar datos locales
      await _updateLocalData(result);
    } else if (result['errorType'] == 'session_expired' || 
               result['errorType'] == 'auth') {
      // Sesión expirada - limpiar pero NO borrar cola
      _sessionToken = null;
      await _secureStorage.delete(key: 'session_token');
      // NO llamar a logout() completo para mantener la cola
    }

    return result;
  }

  /// Actualizar datos locales desde la respuesta
  Future<void> _updateLocalData(Map<String, dynamic> result) async {
    await _dbService.clearWorkOrders();
    
    List<WorkOrder> newOrders = result['newWorkOrders'] ?? [];
    List<WorkOrder> teamOrders = result['teamWorkOrders'] ?? [];
    List<WorkOrder> myOrders = result['myWorkOrders'] ?? [];
    
    await _dbService.insertWorkOrders(newOrders);
    await _dbService.insertWorkOrders(teamOrders);
    await _dbService.insertWorkOrders(myOrders);
  }

  /// Procesar cola después del login
  void _processQueueAfterLogin() {
    // El ConnectivityService procesará la cola automáticamente
    // porque tiene acceso a AuthService y usará el token actual
    Future.delayed(const Duration(seconds: 2), () {
      ConnectivityService().processQueue();
    });
  }

  /// Cerrar sesión
  Future<void> logout() async {
    _currentIdentity = null;
    _sessionToken = null;
    
    await _secureStorage.delete(key: 'identity');
    await _secureStorage.delete(key: 'session_token');
    await _dbService.clearWorkOrders();
    // NOTA: NO limpiar la cola offline para que se pueda procesar al re-login
  }

  Future<List<WorkOrder>> getNewWorkOrders() async {
    return await _dbService.getWorkOrders('new');
  }

  Future<List<WorkOrder>> getTeamWorkOrders() async {
    return await _dbService.getWorkOrders('team');
  }

  Future<List<WorkOrder>> getPersonalWorkOrders() async {
    return await _dbService.getWorkOrders('personal');
  }

  /// Obtener comentarios de una tarea
  Future<Map<String, dynamic>> getTaskComments(String taskId) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa', 'errorType': 'session_expired'};
    }

    final result = await _apiService.getTaskComments(_sessionToken!, taskId);
    
    if (result['errorType'] == 'session_expired') {
      _sessionToken = null;
      await _secureStorage.delete(key: 'session_token');
    }
    
    return result;
  }

  /// Agregar comentario a una tarea
  Future<Map<String, dynamic>> addComment(
    String taskId,
    String comment, {
    String? imageBase64,
    String? imageName,
  }) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa', 'errorType': 'session_expired'};
    }

    final result = await _apiService.addComment(
      _sessionToken!,
      taskId,
      comment,
      imageBase64: imageBase64,
      imageName: imageName,
    );
    
    if (result['errorType'] == 'session_expired') {
      _sessionToken = null;
      await _secureStorage.delete(key: 'session_token');
    }
    
    return result;
  }

  /// Editar un comentario
  Future<Map<String, dynamic>> editComment(String commentId, String comment) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa', 'errorType': 'session_expired'};
    }

    final result = await _apiService.editComment(_sessionToken!, commentId, comment);
    
    if (result['errorType'] == 'session_expired') {
      _sessionToken = null;
      await _secureStorage.delete(key: 'session_token');
    }
    
    return result;
  }

  /// Marcar tarea como completada
  Future<Map<String, dynamic>> completeTask(String taskId) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa', 'errorType': 'session_expired'};
    }

    final result = await _apiService.completeTask(_sessionToken!, taskId);
    
    if (result['errorType'] == 'session_expired') {
      _sessionToken = null;
      await _secureStorage.delete(key: 'session_token');
    }
    
    return result;
  }

  /// Rechazar tarea con razón
  Future<Map<String, dynamic>> rejectTask(String taskId, String reason) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa', 'errorType': 'session_expired'};
    }

    final result = await _apiService.rejectTask(_sessionToken!, taskId, reason);
    
    if (result['errorType'] == 'session_expired') {
      _sessionToken = null;
      await _secureStorage.delete(key: 'session_token');
    }
    
    return result;
  }

  /// Aceptar tarea
  Future<Map<String, dynamic>> acceptTask(String taskId) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa', 'errorType': 'session_expired'};
    }

    final result = await _apiService.acceptTask(_sessionToken!, taskId);
    
    if (result['errorType'] == 'session_expired') {
      _sessionToken = null;
      await _secureStorage.delete(key: 'session_token');
    }
    
    return result;
  }
}
