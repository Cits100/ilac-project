class WorkOrder {
  final String id;
  final String title;
  final String tag;
  final String dueDate;
  final String taskCount;
  final String completionStatus;
  final List<Task> tasks;
  final String type; // 'team', 'personal', or 'new'
  final String? acceptTagUrl;
  final String? rejectTagUrl;

  WorkOrder({
    required this.id,
    required this.title,
    required this.tag,
    required this.dueDate,
    required this.taskCount,
    required this.completionStatus,
    required this.tasks,
    required this.type,
    this.acceptTagUrl,
    this.rejectTagUrl,
  });

  factory WorkOrder.fromJson(Map<String, dynamic> json, String type) {
    return WorkOrder(
      id: json['id'] ?? '',
      title: json['title'] ?? '',
      tag: json['tag'] ?? '',
      dueDate: json['dueDate'] ?? '',
      taskCount: json['taskCount'] ?? '',
      completionStatus: json['completionStatus'] ?? '',
      tasks: (json['tasks'] as List<dynamic>?)
              ?.map((t) => Task.fromJson(t))
              .toList() ??
          [],
      type: type,
      acceptTagUrl: json['acceptTagUrl'],
      rejectTagUrl: json['rejectTagUrl'],
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'tag': tag,
      'dueDate': dueDate,
      'taskCount': taskCount,
      'completionStatus': completionStatus,
      'type': type,
      'acceptTagUrl': acceptTagUrl,
      'rejectTagUrl': rejectTagUrl,
    };
  }

  factory WorkOrder.fromMap(Map<String, dynamic> map, List<Task> tasks) {
    return WorkOrder(
      id: map['id'],
      title: map['title'],
      tag: map['tag'],
      dueDate: map['dueDate'],
      taskCount: map['taskCount'],
      completionStatus: map['completionStatus'],
      tasks: tasks,
      type: map['type'],
      acceptTagUrl: map['acceptTagUrl'],
      rejectTagUrl: map['rejectTagUrl'],
    );
  }
}

class Task {
  final String id;
  final String orderNumber;
  final String status;
  final String dueDate;
  final String dispatchType;
  final String location;
  final String department;
  final String machine;
  final String machinePart;
  final String title;
  final String description;
  final String product;
  final String assignedTo;
  final String detailUrl;
  final TaskDetail? detail;
  final String? acceptUrl;
  final String? rejectUrl;

  Task({
    required this.id,
    required this.orderNumber,
    required this.status,
    required this.dueDate,
    required this.dispatchType,
    required this.location,
    required this.department,
    required this.machine,
    required this.machinePart,
    required this.title,
    required this.description,
    required this.product,
    required this.assignedTo,
    required this.detailUrl,
    this.detail,
    this.acceptUrl,
    this.rejectUrl,
  });

  factory Task.fromJson(Map<String, dynamic> json) {
    return Task(
      id: json['id'] ?? '',
      orderNumber: json['orderNumber'] ?? '',
      status: json['status'] ?? '',
      dueDate: json['dueDate'] ?? '',
      dispatchType: json['dispatchType'] ?? '',
      location: json['location'] ?? '',
      department: json['department'] ?? '',
      machine: json['machine'] ?? '',
      machinePart: json['machinePart'] ?? '',
      title: json['title'] ?? '',
      description: json['description'] ?? '',
      product: json['product'] ?? '',
      assignedTo: json['assignedTo'] ?? '',
      detailUrl: json['detailUrl'] ?? '',
      detail: json['detail'] != null ? TaskDetail.fromJson(json['detail']) : null,
      acceptUrl: json['acceptUrl'],
      rejectUrl: json['rejectUrl'],
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'orderNumber': orderNumber,
      'status': status,
      'dueDate': dueDate,
      'dispatchType': dispatchType,
      'location': location,
      'department': department,
      'machine': machine,
      'machinePart': machinePart,
      'title': title,
      'description': description,
      'product': product,
      'assignedTo': assignedTo,
      'detailUrl': detailUrl,
      'acceptUrl': acceptUrl,
      'rejectUrl': rejectUrl,
    };
  }

  factory Task.fromMap(Map<String, dynamic> map, TaskDetail? detail) {
    return Task(
      id: map['id'],
      orderNumber: map['orderNumber'],
      status: map['status'],
      dueDate: map['dueDate'],
      dispatchType: map['dispatchType'],
      location: map['location'],
      department: map['department'],
      machine: map['machine'],
      machinePart: map['machinePart'],
      title: map['title'],
      description: map['description'],
      product: map['product'],
      assignedTo: map['assignedTo'],
      detailUrl: map['detailUrl'],
      detail: detail,
      acceptUrl: map['acceptUrl'],
      rejectUrl: map['rejectUrl'],
    );
  }
}

class TaskDetail {
  final String department;
  final String location;
  final String machine;
  final String machinePart;
  final String maintenancePoint;
  final String pointCount;
  final String taskType;
  final String applicationMode;
  final String productName;
  final String productVolume;
  final List<String> images;
  final List<String> toolImages;
  final List<String> productImages;
  final List<String> safetyIcons;

  TaskDetail({
    required this.department,
    required this.location,
    required this.machine,
    required this.machinePart,
    required this.maintenancePoint,
    required this.pointCount,
    required this.taskType,
    required this.applicationMode,
    required this.productName,
    required this.productVolume,
    required this.images,
    required this.toolImages,
    required this.productImages,
    required this.safetyIcons,
  });

  factory TaskDetail.fromJson(Map<String, dynamic> json) {
    return TaskDetail(
      department: json['department'] ?? '',
      location: json['location'] ?? '',
      machine: json['machine'] ?? '',
      machinePart: json['machinePart'] ?? '',
      maintenancePoint: json['maintenancePoint'] ?? '',
      pointCount: json['pointCount'] ?? '',
      taskType: json['taskType'] ?? '',
      applicationMode: json['applicationMode'] ?? '',
      productName: json['productName'] ?? '',
      productVolume: json['productVolume'] ?? '',
      images: List<String>.from(json['images'] ?? []),
      toolImages: List<String>.from(json['toolImages'] ?? []),
      productImages: List<String>.from(json['productImages'] ?? []),
      safetyIcons: List<String>.from(json['safetyIcons'] ?? []),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'department': department,
      'location': location,
      'machine': machine,
      'machinePart': machinePart,
      'maintenancePoint': maintenancePoint,
      'pointCount': pointCount,
      'taskType': taskType,
      'applicationMode': applicationMode,
      'productName': productName,
      'productVolume': productVolume,
    };
  }

  factory TaskDetail.fromMap(Map<String, dynamic> map) {
    return TaskDetail(
      department: map['department'] ?? '',
      location: map['location'] ?? '',
      machine: map['machine'] ?? '',
      machinePart: map['machinePart'] ?? '',
      maintenancePoint: map['maintenancePoint'] ?? '',
      pointCount: map['pointCount'] ?? '',
      taskType: map['taskType'] ?? '',
      applicationMode: map['applicationMode'] ?? '',
      productName: map['productName'] ?? '',
      productVolume: map['productVolume'] ?? '',
      images: [],
      toolImages: [],
      productImages: [],
      safetyIcons: [],
    );
  }
}
