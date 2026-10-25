import os
import glob

services = [
    "clearing-service", "margin-service", "position-service", 
    "trade-service", "notification-service", "corporate-action-service", "reference-data-service"
]

for service in services:
    yml_path = f"{service}/src/main/resources/application.yml"
    if not os.path.exists(yml_path): continue
    
    schema_name = service.replace("-service", "").replace("-", "_")
    
    with open(yml_path, 'r') as f:
        lines = f.readlines()
        
    out_lines = []
    for line in lines:
        out_lines.append(line)
        if "dialect: org.hibernate.dialect.PostgreSQLDialect" in line and "default_schema" not in "".join(lines):
            # Try to grab the indentation of the dialect line
            indent = line.split("dialect")[0]
            out_lines.append(f"{indent}default_schema: {schema_name}\n")
        if "locations: classpath:db/migration" in line and "create-schemas" not in "".join(lines):
            indent = line.split("locations")[0]
            out_lines.append(f"{indent}schemas: {schema_name}\n")
            out_lines.append(f"{indent}create-schemas: true\n")
            out_lines.append(f"{indent}default-schema: {schema_name}\n")
            
    with open(yml_path, 'w') as f:
        f.writelines(out_lines)
