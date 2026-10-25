import os
import re

services = [
    'notification-service', 'margin-service', 'clearing-service', 
    'position-service', 'corporate-action-service', 'trade-service'
]

for svc in services:
    path = f"./{svc}/src/main/resources/application.yml"
    if not os.path.exists(path):
        continue
    
    with open(path, 'r') as f:
        content = f.read()

    # If it's already under consumer properties, skip
    if "consumer:\n      group-id:" in content and "properties:\n        spring.json.trusted.packages" in content.split("listener:")[0]:
        print(f"Skipping {svc}, already fixed")
        continue

    # We need to extract properties: \n spring.json.trusted.packages... and put it under consumer
    # Let's just do a simple replacement
    new_content = re.sub(
        r"(      enable-auto-commit: false\n)",
        r"\1      properties:\n        spring.json.trusted.packages: \"com.clearing.shared.events\"\n",
        content
    )
    new_content = re.sub(
        r"(\s+spring\.json\.trusted\.packages: \"com\.clearing\.shared\.events\"\n)",
        "",
        new_content
    )
    
    with open(path, 'w') as f:
        f.write(new_content)
    print(f"Fixed {svc}")

