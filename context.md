แก้ปัญหาข้อมูล README ไม่ตรงกับ frontend directory โดย :

README ระบุว่า frontend ใช้:

Next.js 14
App Router

แต่ใน frontend directory พบทั้ง:

src/main.jsx
vite.config.js
index.html

ซึ่งเป็นโครงสร้างของ Vite/SPA

ในขณะเดียวกันก็มี:

src/app/page.jsx
src/app/layout.jsx

ซึ่งเป็น Next.js App Router

แปลว่า frontend architecture ยัง “ปนกันอยู่”

แก้ไข โดย :

ใช้ Next.js เต็มระบบ

ลบ:

vite.config.js
src/main.jsx
index.html