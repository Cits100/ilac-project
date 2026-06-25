import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';
import '../../utils/app_theme.dart';
import '../../models/work_order.dart';
import '../../models/task_comment.dart';
import '../../services/auth_service.dart';
import '../../services/connectivity_service.dart';

class TaskDetailScreen extends StatefulWidget {
  final Task task;
  final String taskType; // 'new', 'team', or 'personal'

  const TaskDetailScreen({
    super.key,
    required this.task,
    this.taskType = 'personal',
  });

  @override
  State<TaskDetailScreen> createState() => _TaskDetailScreenState();
}

class _TaskDetailScreenState extends State<TaskDetailScreen> {
  final AuthService _authService = AuthService();
  final ConnectivityService _connectivityService = ConnectivityService();
  final TextEditingController _commentController = TextEditingController();
  final TextEditingController _rejectReasonController = TextEditingController();
  final ImagePicker _imagePicker = ImagePicker();
  
  File? _selectedImage;
  bool _isSubmitting = false;
  bool _isCompleting = false;
  bool _isRejecting = false;
  bool _isAccepting = false;
  bool _isLoadingComments = false;
  List<TaskComment> _comments = [];
  String _taskStatus = 'pending'; // 'pending' or 'completed'
  String _taskStatusText = '';
  bool _canComplete = true;

  @override
  void initState() {
    super.initState();
    _taskStatus = widget.task.status;
    _refreshTaskData();
  }

  @override
  void dispose() {
    _commentController.dispose();
    _rejectReasonController.dispose();
    super.dispose();
  }

  Future<void> _refreshTaskData() async {
    setState(() {
      _isLoadingComments = true;
    });

    try {
      final result = await _authService.getTaskDetail(widget.task.id);
      if (result['success'] == true && mounted) {
        setState(() {
          // Actualizar comentarios
          if (result['comments'] != null) {
            _comments = (result['comments'] as List)
                .map((c) => TaskComment.fromJson(c))
                .toList();
          }
          // Actualizar estado
          if (result['status'] != null) {
            _taskStatus = result['status']['status'] ?? 'pending';
            _taskStatusText = result['status']['statusText'] ?? '';
            _canComplete = result['status']['canComplete'] == 'true';
          }
        });
      }
    } catch (e) {
      // Error loading - silent fail
    } finally {
      if (mounted) {
        setState(() {
          _isLoadingComments = false;
        });
      }
    }
  }

  Future<void> _pickImage() async {
    final cameraStatus = await Permission.camera.request();
    final storageStatus = await Permission.photos.request();

    if (cameraStatus.isGranted || storageStatus.isGranted) {
      showModalBottomSheet(
        context: context,
        backgroundColor: AppTheme.darkGrey,
        builder: (context) => SafeArea(
          child: Wrap(
            children: [
              ListTile(
                leading: const Icon(Icons.camera_alt, color: AppTheme.white),
                title: const Text('Tomar foto', style: TextStyle(color: AppTheme.white)),
                onTap: () async {
                  Navigator.pop(context);
                  final image = await _imagePicker.pickImage(source: ImageSource.camera);
                  if (image != null) {
                    setState(() {
                      _selectedImage = File(image.path);
                    });
                  }
                },
              ),
              ListTile(
                leading: const Icon(Icons.photo_library, color: AppTheme.white),
                title: const Text('Seleccionar de galería', style: TextStyle(color: AppTheme.white)),
                onTap: () async {
                  Navigator.pop(context);
                  final image = await _imagePicker.pickImage(source: ImageSource.gallery);
                  if (image != null) {
                    setState(() {
                      _selectedImage = File(image.path);
                    });
                  }
                },
              ),
            ],
          ),
        ),
      );
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Se requieren permisos de cámara y almacenamiento'),
            backgroundColor: AppTheme.darkRed,
          ),
        );
      }
    }
  }

  Future<void> _submitComment() async {
    if (_commentController.text.trim().isEmpty && _selectedImage == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Ingrese un comentario o seleccione una imagen'),
          backgroundColor: AppTheme.darkRed,
        ),
      );
      return;
    }

    setState(() {
      _isSubmitting = true;
    });

    String? imageBase64;
    String? imageName;

    if (_selectedImage != null) {
      final bytes = await _selectedImage!.readAsBytes();
      imageBase64 = base64Encode(bytes);
      imageName = _selectedImage!.path.split('/').last;
    }

    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.addComment(
        widget.task.id,
        _commentController.text.trim(),
        imageBase64: imageBase64,
        imageName: imageName,
      );

      if (result['success'] == true) {
        _commentController.clear();
        setState(() {
          _selectedImage = null;
        });
        _refreshTaskData(); // Reload data after adding comment
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Comentario agregado exitosamente'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        await _connectivityService.addToQueue(
          'comment',
          widget.task.id,
          comment: _commentController.text.trim(),
          imageBase64: imageBase64,
          imageName: imageName,
        );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Comentario guardado para enviar cuando haya conexión'),
              backgroundColor: AppTheme.primaryRed,
            ),
          );
        }
      }
    } else {
      await _connectivityService.addToQueue(
        'comment',
        widget.task.id,
        comment: _commentController.text.trim(),
        imageBase64: imageBase64,
        imageName: imageName,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Comentario guardado para enviar cuando haya conexión'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isSubmitting = false;
    });
  }

  Future<void> _completeTask() async {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Text('Marcar como Realizada', style: TextStyle(color: AppTheme.white)),
        content: const Text(
          '¿Está seguro que desea marcar esta tarea como realizada?',
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
              await _doCompleteTask();
            },
            child: const Text('Confirmar', style: TextStyle(color: AppTheme.lightRed)),
          ),
        ],
      ),
    );
  }

  Future<void> _doCompleteTask() async {
    setState(() {
      _isCompleting = true;
    });

    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.completeTask(widget.task.id);

      if (result['success'] == true) {
        await _refreshTaskData(); // Refresh to show completed status
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Tarea marcada como realizada'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        await _connectivityService.addToQueue('complete', widget.task.id);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Acción guardada para enviar cuando haya conexión'),
              backgroundColor: AppTheme.primaryRed,
            ),
          );
        }
      }
    } else {
      await _connectivityService.addToQueue('complete', widget.task.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Acción guardada para enviar cuando haya conexión'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isCompleting = false;
    });
  }

  Future<void> _rejectTask() async {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Text('Rechazar Tarea', style: TextStyle(color: AppTheme.white)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              'Ingrese el motivo del rechazo:',
              style: TextStyle(color: AppTheme.lightGrey),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _rejectReasonController,
              maxLines: 3,
              decoration: const InputDecoration(
                hintText: 'Motivo del rechazo...',
                hintStyle: TextStyle(color: AppTheme.grey),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              _rejectReasonController.clear();
              Navigator.pop(context);
            },
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () async {
              if (_rejectReasonController.text.trim().isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Ingrese un motivo'),
                    backgroundColor: AppTheme.darkRed,
                  ),
                );
                return;
              }
              Navigator.pop(context);
              await _doRejectTask();
            },
            child: const Text('Rechazar', style: TextStyle(color: AppTheme.lightRed)),
          ),
        ],
      ),
    );
  }

  Future<void> _doRejectTask() async {
    setState(() {
      _isRejecting = true;
    });

    final reason = _rejectReasonController.text.trim();
    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.rejectTask(widget.task.id, reason);

      if (result['success'] == true) {
        _rejectReasonController.clear();
        await _refreshTaskData(); // Refresh to show updated status
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Tarea rechazada'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        await _connectivityService.addToQueue(
          'reject',
          widget.task.id,
          reason: reason,
        );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Acción guardada para enviar cuando haya conexión'),
              backgroundColor: AppTheme.primaryRed,
            ),
          );
        }
      }
    } else {
      await _connectivityService.addToQueue(
        'reject',
        widget.task.id,
        reason: reason,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Acción guardada para enviar cuando haya conexión'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isRejecting = false;
    });
  }

  Future<void> _acceptTask() async {
    setState(() {
      _isAccepting = true;
    });

    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.acceptTask(widget.task.id);

      if (result['success'] == true) {
        await _refreshTaskData(); // Refresh to show updated status
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Tarea aceptada'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        await _connectivityService.addToQueue('accept', widget.task.id);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Acción guardada para enviar cuando haya conexión'),
              backgroundColor: AppTheme.primaryRed,
            ),
          );
        }
      }
    } else {
      await _connectivityService.addToQueue('accept', widget.task.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Acción guardada para enviar cuando haya conexión'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isAccepting = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final task = widget.task;
    final detail = task.detail;
    final isCompleted = _taskStatus == 'completed';
    final isNewTask = widget.taskType == 'new';

    return Scaffold(
      appBar: AppBar(
        title: Text('Tarea #${task.orderNumber}'),
      ),
      body: RefreshIndicator(
        onRefresh: _refreshTaskData,
        color: AppTheme.primaryRed,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Status badge
              if (isCompleted)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  decoration: BoxDecoration(
                    color: Colors.green.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.check_circle, color: Colors.green, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        _taskStatusText.isNotEmpty ? _taskStatusText.toUpperCase() : 'TAREA COMPLETADA',
                        style: const TextStyle(
                          color: Colors.green,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              if (isCompleted) const SizedBox(height: 16),

            // Accept/Reject buttons for new tasks
            if (isNewTask && !isCompleted) ...[
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _isAccepting ? null : _acceptTask,
                      icon: _isAccepting
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppTheme.white,
                              ),
                            )
                          : const Icon(Icons.check),
                      label: const Text('Aceptar'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.green,
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _isRejecting ? null : _rejectTask,
                      icon: _isRejecting
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppTheme.white,
                              ),
                            )
                          : const Icon(Icons.close),
                      label: const Text('Rechazar'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryRed,
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
            ],

            // Task info card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Title
                    Text(
                      task.title,
                      style: const TextStyle(
                        color: AppTheme.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 20,
                      ),
                    ),
                    if (task.description.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        task.description,
                        style: const TextStyle(
                          color: AppTheme.lightGrey,
                          fontSize: 16,
                        ),
                      ),
                    ],
                    const Divider(color: AppTheme.darkGrey, height: 24),
                    
                    // Info rows
                    _buildInfoRow('Ubicación', '${task.location} / ${task.department}'),
                    _buildInfoRow('Máquina', '${task.machine} / ${task.machinePart}'),
                    _buildInfoRow('Producto', task.product),
                    _buildInfoRow('Tipo de despacho', task.dispatchType),
                    _buildInfoRow('Fecha de vencimiento', task.dueDate),
                    if (task.assignedTo.isNotEmpty)
                      _buildInfoRow('Asignado a', task.assignedTo),
                  ],
                ),
              ),
            ),
            
            // Task detail from API
            if (detail != null) ...[
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Detalles del Mantenimiento',
                        style: TextStyle(
                          color: AppTheme.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      const Divider(color: AppTheme.darkGrey, height: 16),
                      _buildInfoRow('Tipo de tarea', detail.taskType),
                      _buildInfoRow('Modo de aplicación', detail.applicationMode),
                      _buildInfoRow('Punto de mantenimiento', detail.maintenancePoint),
                      _buildInfoRow('Cantidad de puntos', detail.pointCount),
                      _buildInfoRow('Producto', detail.productName),
                      _buildInfoRow('Volumen', detail.productVolume),
                    ],
                  ),
                ),
              ),
            ],

            // Comments list section - always show
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.comment, color: AppTheme.primaryRed, size: 20),
                        const SizedBox(width: 8),
                        Text(
                          'Comentarios (${_comments.length})',
                          style: const TextStyle(
                            color: AppTheme.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 16,
                          ),
                        ),
                        if (_isLoadingComments)
                          const Padding(
                            padding: EdgeInsets.only(left: 8),
                            child: SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppTheme.primaryRed,
                              ),
                            ),
                          ),
                        const Spacer(),
                        IconButton(
                          icon: const Icon(Icons.refresh, color: AppTheme.lightGrey, size: 20),
                          onPressed: _refreshTaskData,
                          tooltip: 'Actualizar comentarios',
                        ),
                      ],
                    ),
                    const Divider(color: AppTheme.darkGrey, height: 16),
                    if (_comments.isEmpty && !_isLoadingComments)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 16),
                        child: Center(
                          child: Text(
                            'No hay comentarios',
                            style: TextStyle(color: AppTheme.grey, fontSize: 14),
                          ),
                        ),
                      )
                    else
                      ..._comments.map((comment) => _buildCommentCard(comment)),
                  ],
                ),
              ),
            ),

            // Comment section (not for new tasks)
            if (!isNewTask) ...[
              const SizedBox(height: 24),
              const Text(
                'Agregar Comentario',
                style: TextStyle(
                  color: AppTheme.white,
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                ),
              ),
              const SizedBox(height: 8),
              
              // Image preview
              if (_selectedImage != null)
                Stack(
                  children: [
                    Container(
                      width: double.infinity,
                      height: 200,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(8),
                        image: DecorationImage(
                          image: FileImage(_selectedImage!),
                          fit: BoxFit.cover,
                        ),
                      ),
                    ),
                    Positioned(
                      top: 8,
                      right: 8,
                      child: CircleAvatar(
                        backgroundColor: AppTheme.darkRed,
                        radius: 16,
                        child: IconButton(
                          icon: const Icon(Icons.close, size: 16, color: AppTheme.white),
                          onPressed: () {
                            setState(() {
                              _selectedImage = null;
                            });
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              if (_selectedImage != null) const SizedBox(height: 8),
              
              // Comment input
              TextField(
                controller: _commentController,
                maxLines: 3,
                decoration: const InputDecoration(
                  hintText: 'Escriba su comentario...',
                  hintStyle: TextStyle(color: AppTheme.grey),
                ),
              ),
              const SizedBox(height: 8),
              
              // Action buttons
              Row(
                children: [
                  IconButton(
                    onPressed: _pickImage,
                    icon: const Icon(Icons.image, color: AppTheme.primaryRed),
                    tooltip: 'Agregar imagen',
                  ),
                  IconButton(
                    onPressed: _pickImage,
                    icon: const Icon(Icons.camera_alt, color: AppTheme.primaryRed),
                    tooltip: 'Tomar foto',
                  ),
                  const Spacer(),
                  
                  ElevatedButton.icon(
                    onPressed: _isSubmitting ? null : _submitComment,
                    icon: _isSubmitting
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: AppTheme.white,
                            ),
                          )
                        : const Icon(Icons.send),
                    label: const Text('Enviar'),
                  ),
                ],
              ),
            ],
            const SizedBox(height: 16),
          ],
        ),
        ), // RefreshIndicator
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    if (value.isEmpty) return const SizedBox.shrink();
    
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(
              '$label:',
              style: const TextStyle(
                color: AppTheme.grey,
                fontSize: 14,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: AppTheme.white,
                fontSize: 14,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCommentCard(TaskComment comment) {
    // Check if comment belongs to current user
    final currentUser = _authService.currentIdentity ?? '';
    final isOwnComment = comment.author.isNotEmpty && 
        (comment.author.toLowerCase().contains(currentUser.split('@').first.toLowerCase()) ||
         currentUser.toLowerCase().contains(comment.author.toLowerCase()));

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.darkGrey.withOpacity(0.5),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppTheme.grey.withOpacity(0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header with author, date and edit button
          Row(
            children: [
              const Icon(Icons.person, size: 16, color: AppTheme.lightGrey),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  comment.author,
                  style: const TextStyle(
                    color: AppTheme.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                  ),
                ),
              ),
              if (comment.date.isNotEmpty)
                Text(
                  comment.date,
                  style: const TextStyle(
                    color: AppTheme.grey,
                    fontSize: 11,
                  ),
                ),
              if (isOwnComment && comment.id.isNotEmpty)
                IconButton(
                  icon: const Icon(Icons.edit, size: 18, color: AppTheme.primaryRed),
                  onPressed: () => _editComment(comment),
                  tooltip: 'Editar comentario',
                ),
            ],
          ),
          const SizedBox(height: 8),
          
          // Comment text
          if (comment.text.isNotEmpty)
            Text(
              comment.text,
              style: const TextStyle(
                color: AppTheme.lightGrey,
                fontSize: 14,
              ),
            ),
          
          // Image preview
          if (comment.imageUrl.isNotEmpty) ...[
            const SizedBox(height: 8),
            Container(
              height: 80,
              width: 80,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: AppTheme.darkGrey,
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Image.network(
                  comment.imageUrl,
                  fit: BoxFit.cover,
                  errorBuilder: (context, error, stackTrace) {
                    return const Center(
                      child: Icon(Icons.broken_image, color: AppTheme.grey, size: 32),
                    );
                  },
                ),
              ),
            ),
            const SizedBox(height: 4),
            Text(
              comment.fileName.isNotEmpty ? comment.fileName : 'Imagen adjunta',
              style: const TextStyle(color: AppTheme.grey, fontSize: 11),
            ),
          ],
        ],
      ),
    );
  }

  void _editComment(TaskComment comment) {
    final editController = TextEditingController(text: comment.text);
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Text('Editar Comentario', style: TextStyle(color: AppTheme.white)),
        content: TextField(
          controller: editController,
          maxLines: 5,
          decoration: const InputDecoration(
            hintText: 'Escriba su comentario...',
            hintStyle: TextStyle(color: AppTheme.grey),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () async {
              if (editController.text.trim().isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('El comentario no puede estar vacío'),
                    backgroundColor: AppTheme.darkRed,
                  ),
                );
                return;
              }
              
              Navigator.pop(context);
              
              final result = await _authService.editComment(
                comment.id,
                editController.text.trim(),
              );
              
              if (result['success'] == true) {
                _refreshTaskData(); // Reload data after editing
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Comentario editado exitosamente'),
                      backgroundColor: Colors.green,
                    ),
                  );
                }
              } else {
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(result['message'] ?? 'Error al editar comentario'),
                      backgroundColor: AppTheme.darkRed,
                    ),
                  );
                }
              }
            },
            child: const Text('Guardar', style: TextStyle(color: AppTheme.primaryRed)),
          ),
        ],
      ),
    );
  }
}
