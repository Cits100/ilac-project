import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import '../models/work_order.dart';

class DatabaseService {
  static final DatabaseService _instance = DatabaseService._internal();
  factory DatabaseService() => _instance;
  DatabaseService._internal();

  static Database? _database;

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    String path = join(await getDatabasesPath(), 'ilac_app.db');
    return await openDatabase(
      path,
      version: 2,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    await _createTables(db);
  }

  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      // Add new columns for accept/reject URLs
      await db.execute('ALTER TABLE work_orders ADD COLUMN acceptTagUrl TEXT');
      await db.execute('ALTER TABLE work_orders ADD COLUMN rejectTagUrl TEXT');
      await db.execute('ALTER TABLE tasks ADD COLUMN acceptUrl TEXT');
      await db.execute('ALTER TABLE tasks ADD COLUMN rejectUrl TEXT');
    }
  }

  Future<void> _createTables(Database db) async {
    await db.execute('''
      CREATE TABLE work_orders (
        id TEXT PRIMARY KEY,
        title TEXT,
        tag TEXT,
        dueDate TEXT,
        taskCount TEXT,
        completionStatus TEXT,
        type TEXT,
        acceptTagUrl TEXT,
        rejectTagUrl TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE tasks (
        id TEXT PRIMARY KEY,
        workOrderId TEXT,
        orderNumber TEXT,
        status TEXT,
        dueDate TEXT,
        dispatchType TEXT,
        location TEXT,
        department TEXT,
        machine TEXT,
        machinePart TEXT,
        title TEXT,
        description TEXT,
        product TEXT,
        assignedTo TEXT,
        detailUrl TEXT,
        acceptUrl TEXT,
        rejectUrl TEXT,
        FOREIGN KEY (workOrderId) REFERENCES work_orders(id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE task_details (
        taskId TEXT PRIMARY KEY,
        department TEXT,
        location TEXT,
        machine TEXT,
        machinePart TEXT,
        maintenancePoint TEXT,
        pointCount TEXT,
        taskType TEXT,
        applicationMode TEXT,
        productName TEXT,
        productVolume TEXT,
        FOREIGN KEY (taskId) REFERENCES tasks(id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE offline_queue (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        actionType TEXT,
        taskId TEXT,
        comment TEXT,
        imageBase64 TEXT,
        imageName TEXT,
        reason TEXT,
        createdAt TEXT,
        retryCount INTEGER DEFAULT 0
      )
    ''');

    await db.execute('''
      CREATE TABLE user_session (
        id INTEGER PRIMARY KEY,
        identity TEXT,
        encryptedPassword TEXT,
        isLoggedIn INTEGER DEFAULT 0
      )
    ''');
  }

  // ========== WORK ORDERS ==========

  Future<void> insertWorkOrders(List<WorkOrder> orders) async {
    final db = await database;
    final batch = db.batch();
    
    for (var order in orders) {
      batch.insert(
        'work_orders',
        order.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      
      for (var task in order.tasks) {
        batch.insert(
          'tasks',
          {...task.toMap(), 'workOrderId': order.id},
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
        
        if (task.detail != null) {
          batch.insert(
            'task_details',
            {...task.detail!.toMap(), 'taskId': task.id},
            conflictAlgorithm: ConflictAlgorithm.replace,
          );
        }
      }
    }
    
    await batch.commit(noResult: true);
  }

  Future<List<WorkOrder>> getWorkOrders(String type) async {
    final db = await database;
    final List<Map<String, dynamic>> orderMaps = await db.query(
      'work_orders',
      where: 'type = ?',
      whereArgs: [type],
    );

    List<WorkOrder> orders = [];
    for (var orderMap in orderMaps) {
      final List<Map<String, dynamic>> taskMaps = await db.query(
        'tasks',
        where: 'workOrderId = ?',
        whereArgs: [orderMap['id']],
      );

      List<Task> tasks = [];
      for (var taskMap in taskMaps) {
        final List<Map<String, dynamic>> detailMaps = await db.query(
          'task_details',
          where: 'taskId = ?',
          whereArgs: [taskMap['id']],
        );

        TaskDetail? detail;
        if (detailMaps.isNotEmpty) {
          detail = TaskDetail.fromMap(detailMaps.first);
        }

        tasks.add(Task.fromMap(taskMap, detail));
      }

      orders.add(WorkOrder.fromMap(orderMap, tasks));
    }

    return orders;
  }

  Future<void> clearWorkOrders() async {
    final db = await database;
    await db.delete('task_details');
    await db.delete('tasks');
    await db.delete('work_orders');
  }

  // ========== OFFLINE QUEUE ==========

  Future<void> addToQueue(String actionType, String taskId,
      {String? comment, String? imageBase64, String? imageName, String? reason}) async {
    final db = await database;
    await db.insert('offline_queue', {
      'actionType': actionType,
      'taskId': taskId,
      'comment': comment,
      'imageBase64': imageBase64,
      'imageName': imageName,
      'reason': reason,
      'createdAt': DateTime.now().toIso8601String(),
      'retryCount': 0,
    });
  }

  Future<List<Map<String, dynamic>>> getQueueItems() async {
    final db = await database;
    return await db.query('offline_queue', orderBy: 'createdAt ASC');
  }

  Future<void> removeQueueItem(int id) async {
    final db = await database;
    await db.delete('offline_queue', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> incrementRetryCount(int id) async {
    final db = await database;
    await db.rawUpdate(
      'UPDATE offline_queue SET retryCount = retryCount + 1 WHERE id = ?',
      [id],
    );
  }

  // ========== USER SESSION ==========

  Future<void> saveSession(String identity, String encryptedPassword) async {
    final db = await database;
    await db.delete('user_session');
    await db.insert('user_session', {
      'id': 1,
      'identity': identity,
      'encryptedPassword': encryptedPassword,
      'isLoggedIn': 1,
    });
  }

  Future<Map<String, dynamic>?> getSession() async {
    final db = await database;
    final List<Map<String, dynamic>> maps = await db.query('user_session');
    if (maps.isNotEmpty) {
      return maps.first;
    }
    return null;
  }

  Future<void> clearSession() async {
    final db = await database;
    await db.delete('user_session');
  }
}
