import 'package:flutter/material.dart';
import '../../utils/app_theme.dart';
import '../../services/auth_service.dart';
import '../../services/connectivity_service.dart';
import '../../models/work_order.dart';
import '../login/login_screen.dart';
import '../work_order/work_order_list_screen.dart';
import '../settings/settings_screen.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final AuthService _authService = AuthService();
  final ConnectivityService _connectivityService = ConnectivityService();
  
  List<WorkOrder> _newOrders = [];
  List<WorkOrder> _teamOrders = [];
  List<WorkOrder> _personalOrders = [];
  bool _isLoading = true;
  bool _isRefreshing = false;
  int _pendingQueueCount = 0;
  int _failedQueueCount = 0;

  @override
  void initState() {
    super.initState();
    _loadData();
    
    // Configurar callback para acciones fallidas
    _connectivityService.onActionFailed = (actionType, taskId, error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Acción fallida: $actionType para tarea $taskId'),
            backgroundColor: AppTheme.darkRed,
            duration: const Duration(seconds: 5),
            action: SnackBarAction(
              label: 'Ver',
              textColor: AppTheme.white,
              onPressed: () => _showFailedActions(),
            ),
          ),
        );
      }
    };
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
    });

    try {
      // Procesar cola de acciones pendientes
      await _connectivityService.processQueue();

      final newOrders = await _authService.getNewWorkOrders();
      final teamOrders = await _authService.getTeamWorkOrders();
      final personalOrders = await _authService.getPersonalWorkOrders();
      final queueCount = await _connectivityService.getQueueCount();
      final failedCount = await _connectivityService.getFailedQueueCount();

      setState(() {
        _newOrders = newOrders;
        _teamOrders = teamOrders;
        _personalOrders = personalOrders;
        _pendingQueueCount = queueCount;
        _failedQueueCount = failedCount;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _refreshData() async {
    setState(() {
      _isRefreshing = true;
    });

    final result = await _authService.refreshData();

    if (result['success'] == true) {
      await _loadData();
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(result['message'] ?? 'Error al actualizar'),
            backgroundColor: AppTheme.darkRed,
          ),
        );
      }
    }

    setState(() {
      _isRefreshing = false;
    });
  }

  Future<void> _logout() async {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Text('Cerrar Sesión', style: TextStyle(color: AppTheme.white)),
        content: const Text(
          '¿Está seguro que desea cerrar sesión?',
          style: TextStyle(color: AppTheme.lightGrey),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(context);
              await _authService.logout();
              if (mounted) {
                Navigator.of(context).pushReplacement(
                  MaterialPageRoute(builder: (_) => const LoginScreen()),
                );
              }
            },
            child: const Text('Cerrar Sesión', style: TextStyle(color: AppTheme.lightRed)),
          ),
        ],
      ),
    );
  }

  void _showFailedActions() async {
    final failedItems = await _connectivityService.getFailedQueueItems();
    
    if (!mounted) return;
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Row(
          children: [
            Icon(Icons.error_outline, color: AppTheme.lightRed),
            SizedBox(width: 8),
            Text('Acciones Fallidas', style: TextStyle(color: AppTheme.white)),
          ],
        ),
        content: SizedBox(
          width: double.maxFinite,
          child: failedItems.isEmpty
              ? const Text(
                  'No hay acciones fallidas',
                  style: TextStyle(color: AppTheme.lightGrey),
                )
              : ListView.builder(
                  shrinkWrap: true,
                  itemCount: failedItems.length,
                  itemBuilder: (context, index) {
                    final item = failedItems[index];
                    return Card(
                      margin: const EdgeInsets.only(bottom: 8),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              _getActionTypeName(item['actionType']),
                              style: const TextStyle(
                                color: AppTheme.white,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Tarea: ${item['taskId']}',
                              style: const TextStyle(color: AppTheme.lightGrey),
                            ),
                            if (item['lastError'] != null) ...[
                              const SizedBox(height: 4),
                              Text(
                                'Error: ${item['lastError']}',
                                style: const TextStyle(color: AppTheme.lightRed, fontSize: 12),
                              ),
                            ],
                          ],
                        ),
                      ),
                    );
                  },
                ),
        ),
        actions: [
          TextButton(
            onPressed: () {
              _connectivityService.clearFailedQueueItems();
              Navigator.pop(context);
              _loadData();
            },
            child: const Text('Limpiar todo', style: TextStyle(color: AppTheme.lightRed)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cerrar'),
          ),
        ],
      ),
    );
  }

  String _getActionTypeName(String? actionType) {
    switch (actionType) {
      case 'comment': return 'Agregar comentario';
      case 'complete': return 'Marcar como completada';
      case 'reject': return 'Rechazar tarea';
      case 'accept': return 'Aceptar tarea';
      default: return 'Acción desconocida';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('ILAC Interflon'),
        actions: [
          if (_failedQueueCount > 0)
            IconButton(
              icon: Badge(
                label: Text('$_failedQueueCount'),
                backgroundColor: AppTheme.darkRed,
                child: const Icon(Icons.error_outline),
              ),
              onPressed: _showFailedActions,
              tooltip: 'Acciones fallidas',
            ),
          if (_pendingQueueCount > 0)
            IconButton(
              icon: Badge(
                label: Text('$_pendingQueueCount'),
                child: const Icon(Icons.cloud_upload),
              ),
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text('$_pendingQueueCount acciones pendientes'),
                  ),
                );
              },
              tooltip: 'Acciones pendientes',
            ),
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () async {
              final result = await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const SettingsScreen()),
              );
              if (result == true) {
                _loadData();
              }
            },
            tooltip: 'Configuración',
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: _logout,
            tooltip: 'Cerrar sesión',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.primaryRed),
            )
          : RefreshIndicator(
              onRefresh: _refreshData,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // User info
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Row(
                        children: [
                          const CircleAvatar(
                            backgroundColor: AppTheme.primaryRed,
                            radius: 24,
                            child: Icon(Icons.person, color: AppTheme.white),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  _authService.currentIdentity ?? 'Usuario',
                                  style: const TextStyle(
                                    color: AppTheme.white,
                                    fontWeight: FontWeight.bold,
                                    fontSize: 16,
                                  ),
                                ),
                                const Text(
                                  'Técnico de Mantenimiento',
                                  style: TextStyle(
                                    color: AppTheme.lightGrey,
                                    fontSize: 14,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          if (_isRefreshing)
                            const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppTheme.primaryRed,
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),

                  // New Work Orders (Nuevas ordenes de trabajo)
                  _buildSection(
                    title: 'Nuevas Órdenes de Trabajo',
                    icon: Icons.new_releases,
                    count: _newOrders.fold(0, (sum, wo) => sum + wo.tasks.length),
                    orders: _newOrders,
                    type: 'new',
                    color: Colors.orange,
                  ),
                  const SizedBox(height: 16),

                  // Team Work Orders
                  _buildSection(
                    title: 'Órdenes de Trabajo de Equipo',
                    icon: Icons.group,
                    count: _teamOrders.fold(0, (sum, wo) => sum + wo.tasks.length),
                    orders: _teamOrders,
                    type: 'team',
                  ),
                  const SizedBox(height: 16),

                  // Personal Work Orders
                  _buildSection(
                    title: 'Mis Órdenes de Trabajo',
                    icon: Icons.person,
                    count: _personalOrders.fold(0, (sum, wo) => sum + wo.tasks.length),
                    orders: _personalOrders,
                    type: 'personal',
                  ),
                ],
              ),
            ),
    );
  }

  Widget _buildSection({
    required String title,
    required IconData icon,
    required int count,
    required List<WorkOrder> orders,
    required String type,
    Color? color,
  }) {
    final sectionColor = color ?? AppTheme.primaryRed;
    
    return Card(
      child: InkWell(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => WorkOrderListScreen(
                title: title,
                workOrders: orders,
                type: type,
              ),
            ),
          );
        },
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(icon, color: sectionColor, size: 28),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(
                        color: AppTheme.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: count > 0 ? sectionColor : AppTheme.darkGrey,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Text(
                      '$count tareas',
                      style: TextStyle(
                        color: count > 0 ? AppTheme.white : AppTheme.grey,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
              if (orders.isNotEmpty) ...[
                const SizedBox(height: 12),
                const Divider(color: AppTheme.darkGrey),
                const SizedBox(height: 8),
                ...orders.map((wo) => Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Row(
                    children: [
                      Text(
                        wo.tag,
                        style: TextStyle(
                          color: sectionColor,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          wo.title,
                          style: const TextStyle(color: AppTheme.lightGrey),
                        ),
                      ),
                      Text(
                        wo.completionStatus,
                        style: const TextStyle(
                          color: AppTheme.grey,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                )),
              ] else ...[
                const SizedBox(height: 12),
                const Text(
                  'No hay órdenes de trabajo',
                  style: TextStyle(color: AppTheme.grey),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
