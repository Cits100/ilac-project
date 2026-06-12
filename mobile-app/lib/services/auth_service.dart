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
  String? _currentCredential;

  String? get currentIdentity => _currentIdentity;
  bool get isLoggedIn => _currentIdentity != null;

  Future<Map<String, dynamic>> login(String identity, String credential) async {
    final result = await _apiService.login(identity, credential);

    if (result['success'] == true) {
      _currentIdentity = identity;
      _currentCredential = credential;

      // Save session
      await _dbService.saveSession(identity, credential);
      await _secureStorage.write(key: 'identity', value: identity);
      await _secureStorage.write(key: 'credential', value: credential);

      // Save work orders to local DB
      await _dbService.clearWorkOrders();
      
      List<WorkOrder> teamOrders = result['teamWorkOrders'] ?? [];
      List<WorkOrder> myOrders = result['myWorkOrders'] ?? [];
      
      await _dbService.insertWorkOrders(teamOrders);
      await _dbService.insertWorkOrders(myOrders);
    }

    return result;
  }

  Future<bool> tryAutoLogin() async {
    try {
      final identity = await _secureStorage.read(key: 'identity');
      final credential = await _secureStorage.read(key: 'credential');

      if (identity == null || credential == null) {
        return false;
      }

      _currentIdentity = identity;
      _currentCredential = credential;

      // Try to login again to refresh data
      final result = await _apiService.login(identity, credential);
      
      if (result['success'] == true) {
        // Update local DB
        await _dbService.clearWorkOrders();
        
        List<WorkOrder> teamOrders = result['teamWorkOrders'] ?? [];
        List<WorkOrder> myOrders = result['myWorkOrders'] ?? [];
        
        await _dbService.insertWorkOrders(teamOrders);
        await _dbService.insertWorkOrders(myOrders);
        
        return true;
      } else {
        // Login failed, clear session
        await logout();
        return false;
      }
    } catch (e) {
      // If offline, try to use cached data
      final session = await _dbService.getSession();
      if (session != null) {
        _currentIdentity = session['identity'];
        _currentCredential = session['encryptedPassword'];
        return true;
      }
      return false;
    }
  }

  Future<void> logout() async {
    _currentIdentity = null;
    _currentCredential = null;
    
    await _secureStorage.delete(key: 'identity');
    await _secureStorage.delete(key: 'credential');
    await _dbService.clearSession();
    await _dbService.clearWorkOrders();
  }

  Future<List<WorkOrder>> getTeamWorkOrders() async {
    return await _dbService.getWorkOrders('team');
  }

  Future<List<WorkOrder>> getPersonalWorkOrders() async {
    return await _dbService.getWorkOrders('personal');
  }

  Future<Map<String, dynamic>> refreshData() async {
    if (_currentIdentity == null || _currentCredential == null) {
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    final result = await _apiService.login(_currentIdentity!, _currentCredential!);

    if (result['success'] == true) {
      await _dbService.clearWorkOrders();
      
      List<WorkOrder> teamOrders = result['teamWorkOrders'] ?? [];
      List<WorkOrder> myOrders = result['myWorkOrders'] ?? [];
      
      await _dbService.insertWorkOrders(teamOrders);
      await _dbService.insertWorkOrders(myOrders);
    }

    return result;
  }

  Future<Map<String, dynamic>> addComment(
    String taskId,
    String comment, {
    String? imageBase64,
    String? imageName,
  }) async {
    if (_currentIdentity == null || _currentCredential == null) {
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    return await _apiService.addComment(
      _currentIdentity!,
      _currentCredential!,
      taskId,
      comment,
      imageBase64: imageBase64,
      imageName: imageName,
    );
  }

  Future<Map<String, dynamic>> completeTask(String taskId) async {
    if (_currentIdentity == null || _currentCredential == null) {
      return {'success': false, 'message': 'No hay sesión activa'};
    }

    return await _apiService.completeTask(
      _currentIdentity!,
      _currentCredential!,
      taskId,
    );
  }
}
