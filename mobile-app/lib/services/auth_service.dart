import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../services/api_service.dart';
import '../services/database_service.dart';
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
      // Si el token expiró, el login fallará y deberá hacer login manual
      return true;
    } catch (e) {
      await logout();
      return false;
    }
  }

  /// Refrescar datos del dashboard
  Future<Map<String, dynamic>> refreshData() async {
    if (_currentIdentity == null) {
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    // Para refrescar, necesitamos hacer login de nuevo
    // porque el backend no tiene endpoint de refresh con token
    final identity = await _secureStorage.read(key: 'identity');
    if (identity == null) {
      return {'success': false, 'message': 'No hay credenciales guardadas'};
    }

    // El usuario debe hacer login manual para refrescar
    return {
      'success': false, 
      'message': 'Sesión expirada. Por favor, inicie sesión nuevamente.',
      'errorType': 'session_expired'
    };
  }

  /// Cerrar sesión
  Future<void> logout() async {
    _currentIdentity = null;
    _sessionToken = null;
    
    await _secureStorage.delete(key: 'identity');
    await _secureStorage.delete(key: 'session_token');
    await _dbService.clearWorkOrders();
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
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    final result = await _apiService.getTaskComments(_sessionToken!, taskId);
    
    // Si la sesión expiró, limpiar
    if (result['errorType'] == 'session_expired') {
      await logout();
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
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    final result = await _apiService.addComment(
      _sessionToken!,
      taskId,
      comment,
      imageBase64: imageBase64,
      imageName: imageName,
    );
    
    // Si la sesión expiró, limpiar
    if (result['errorType'] == 'session_expired') {
      await logout();
    }
    
    return result;
  }

  /// Editar un comentario
  Future<Map<String, dynamic>> editComment(String commentId, String comment) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    final result = await _apiService.editComment(_sessionToken!, commentId, comment);
    
    // Si la sesión expiró, limpiar
    if (result['errorType'] == 'session_expired') {
      await logout();
    }
    
    return result;
  }

  /// Marcar tarea como completada
  Future<Map<String, dynamic>> completeTask(String taskId) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    final result = await _apiService.completeTask(_sessionToken!, taskId);
    
    // Si la sesión expiró, limpiar
    if (result['errorType'] == 'session_expired') {
      await logout();
    }
    
    return result;
  }

  /// Rechazar tarea con razón
  Future<Map<String, dynamic>> rejectTask(String taskId, String reason) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    final result = await _apiService.rejectTask(_sessionToken!, taskId, reason);
    
    // Si la sesión expiró, limpiar
    if (result['errorType'] == 'session_expired') {
      await logout();
    }
    
    return result;
  }

  /// Aceptar tarea
  Future<Map<String, dynamic>> acceptTask(String taskId) async {
    if (_sessionToken == null) {
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    final result = await _apiService.acceptTask(_sessionToken!, taskId);
    
    // Si la sesión expiró, limpiar
    if (result['errorType'] == 'session_expired') {
      await logout();
    }
    
    return result;
  }
}
