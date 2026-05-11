1. ตรวจสอบ architecture consistency ของ projfect

มี stack เดียว frontend หรือไม่

2. ตรวจสอบ proof ของ performance

มีรายการดังด่อไปนี้หรือไม่ :

benchmark docs
screenshots
metrics
methodology

3. ตรวจสอบ production security

มีรายการดังต่อไปนี้หรือไม่ :

TLS MQTT
per-device auth
secret rotation
mTLS

4. ตรวจสอบ project มีการแยก event streaming layer หรือไม่

อ้างอิงจากรายการดังต่อไปนี้ :

Kafka
async processing
consumer group

5. ตรวจสอบว่ามี Kubernetes deployment หรือไม่

โดยอ้างอิงจากรายการดังต่อไปนี้ :

Helm
HPA
ingress
rolling deployment

6. ตรวจสอบว่ามี distributed websocket strategyหรือไม่

7. ตรวจสอบว่ามี real production documentation หรือไม่

โดยอ้างอิงจากรายดังต่อไปนี้ :

incident flow
scaling limit
failure testing
chaos testing
capacity planning

โดยทุกรายการที่ตรวจสอบนั้น ให้เทียบ code กับ ไฟล์ config ต้องตรงกัน